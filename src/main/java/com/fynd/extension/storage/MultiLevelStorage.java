package com.fynd.extension.storage;

import redis.clients.jedis.*;
import com.mongodb.client.*;
import org.bson.Document;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MultiLevelStorage extends BaseStorage {

    private boolean isClusterMode;
    private JedisPool jedisPool;
    private JedisCluster jedisCluster;
    private String prefixKey;
    private JedisSentinelPool jedisSentinelPool;
    private MongoCollection<Document> mongoCollection;
    private static final String DEFAULT_COLLECTION_NAME = "fdk_ext_acc_tokens";

    public MultiLevelStorage(JedisPool jedisPool, MongoDatabase mongoDatabase, String prefixKey, Map<String, String> options) {
        super(prefixKey);
        String collectionName = options.getOrDefault("collectionName", DEFAULT_COLLECTION_NAME);
        this.jedisPool = jedisPool;
        this.mongoCollection = mongoDatabase.getCollection(collectionName);
        this.prefixKey = prefixKey;
        this.isClusterMode = false;
        ensureTTLIndex();
    }

    public MultiLevelStorage(JedisCluster jedisCluster, MongoDatabase mongoDatabase, String prefixKey, Map<String, String> options) {
        super(prefixKey);
        String collectionName = options.getOrDefault("collectionName", DEFAULT_COLLECTION_NAME);
        this.jedisCluster = jedisCluster;
        this.mongoCollection = mongoDatabase.getCollection(collectionName);
        this.prefixKey = prefixKey;
        this.isClusterMode = true;
        ensureTTLIndex();
    }

    public MultiLevelStorage(JedisSentinelPool jedisSentinelPool, MongoDatabase mongoDatabase, String prefixKey, Map<String, String> options) {
        super(prefixKey);
        String collectionName = options.getOrDefault("collectionName", DEFAULT_COLLECTION_NAME);
        this.jedisSentinelPool = jedisSentinelPool;
        this.mongoCollection = mongoDatabase.getCollection(collectionName);
        this.prefixKey = prefixKey;
        this.isClusterMode = false;
        ensureTTLIndex();
    }

    @Override
    public String get(String key) {
        String redisKey = generateKey(key);
        String value = fetchFromRedis(redisKey);
        if (value == null) {
            value = fetchFromMongo(redisKey);
            if (value != null) {
                set(key, value);
            }
        }
        return value;
    }

    @Override
    public String set(String key, String value) {
        String redisKey = generateKey(key);
        storeInMongo(redisKey, value);
        return storeInRedis(redisKey, value);
    }

    @Override
    public Long del(String key) {
        String redisKey = generateKey(key);
        deleteFromMongo(redisKey);
        return deleteFromRedis(redisKey);
    }

    @Override
    public String setex(String key, int ttl, String value) {
        String redisKey = generateKey(key);
        storeInMongo(redisKey, value, ttl);
        return storeInRedisWithTTL(redisKey, ttl, value);
    }

    private String generateKey(String key) {
        return super.prefixKey + key;
    }

    private void ensureTTLIndex() {
        List<Document> indexes = mongoCollection.listIndexes().into(new java.util.ArrayList<>());
        boolean ttlIndexExists = indexes.stream()
                .anyMatch(index -> index.get("key", Document.class).containsKey("expireAt") &&
                        index.containsKey("expireAfterSeconds"));
        if (!ttlIndexExists) {
            mongoCollection.createIndex(new Document("expireAt", 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
        }
    }

    private String fetchFromMongo(String key) {
        Document doc = mongoCollection.find(new Document("key", key)).first();
        if (doc != null) {
            Date expireAt = doc.getDate("expireAt");
            if (expireAt != null && expireAt.getTime() < System.currentTimeMillis()) {
                deleteFromMongo(key);
                return null;
            }
            return doc.getString("value");
        }
        return null;
    }

    private void storeInMongo(String key, String value, int ttl) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + (ttl * 1000L));
        Document doc = new Document("key", key)
                .append("value", value)
                .append("updatedAt", now)
                .append("expireAt", expireAt);
        mongoCollection.replaceOne(new Document("key", key), doc, new ReplaceOptions().upsert(true));
    }

    private void storeInMongo(String key, String value) {
        Date now = new Date();
        Document doc = new Document("key", key)
                .append("value", value)
                .append("updatedAt", now);
        mongoCollection.replaceOne(new Document("key", key), doc, new ReplaceOptions().upsert(true));
    }

    private String fetchFromRedis(String key) {
        if (isClusterMode) {
            return jedisCluster.get(key);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.get(key);
            }
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(key);
            }
        }
    }

    private String storeInRedis(String key, String value) {
        if (isClusterMode) {
            return jedisCluster.set(key, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.set(key, value);
            }
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.set(key, value);
            }
        }
    }

    private String storeInRedisWithTTL(String key, int ttl, String value) {
        if (isClusterMode) {
            return jedisCluster.setex(key, ttl, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.setex(key, ttl, value);
            }
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.setex(key, ttl, value);
            }
        }
    }

    private Long deleteFromRedis(String key) {
        if (isClusterMode) {
            return jedisCluster.del(key);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.del(key);
            }
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.del(key);
            }
        }
    }

    private void deleteFromMongo(String key) {
        mongoCollection.deleteOne(new Document("key", key));
    }
}
