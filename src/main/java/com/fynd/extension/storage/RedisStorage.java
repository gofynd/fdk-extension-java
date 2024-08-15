package com.fynd.extension.storage;


import redis.clients.jedis.*;
import java.util.Map;

public class RedisStorage extends BaseStorage {

    private boolean isClusterMode;
    private JedisPool jedisPool;
    private JedisCluster jedisCluster;
    private String prefixKey;
    private JedisSentinelPool jedisSentinelPool;

    public RedisStorage(JedisPool jedisPool, String prefixKey) {
        super(prefixKey);
        this.jedisPool = jedisPool;
        this.prefixKey = prefixKey;
        this.isClusterMode = false;
    }

    public RedisStorage(JedisCluster jedisCluster, String prefixKey) {
        super(prefixKey);
        this.jedisCluster = jedisCluster;
        this.prefixKey = prefixKey;
        this.isClusterMode = true;
    }

    public RedisStorage(JedisSentinelPool jedisSentinelPool, String prefixKey) {
        super(prefixKey);
        this.jedisSentinelPool = jedisSentinelPool;
        this.prefixKey = prefixKey;
        this.isClusterMode = false; // Sentinel doesn't use clustering
    }

    @Override
    public String get(String key) {
        if (isClusterMode) {
            return jedisCluster.get(super.prefixKey + key);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.get(super.prefixKey + key);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(super.prefixKey + key);
            }
        }
    }

    @Override
    public String set(String key, String value) {
        if (isClusterMode) {
            return jedisCluster.set(super.prefixKey + key, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.set(super.prefixKey + key, value);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.set(super.prefixKey + key, value);
            }
        }
    }

    @Override
    public Long del(String key) {
        if (isClusterMode) {
            return jedisCluster.del(super.prefixKey + key);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.del(super.prefixKey + key);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.del(super.prefixKey + key);
            }
        }
    }

    @Override
    public String setex(String key, int ttl, String value) {
        if (isClusterMode) {
            return jedisCluster.setex(super.prefixKey + key, ttl, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.setex(super.prefixKey + key, ttl, value);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.setex(super.prefixKey + key, ttl, value);
            }
        }
    }

    @Override
    public String hget(String key, String hashKey) {
        if (isClusterMode) {
            return jedisCluster.hget(super.prefixKey + key, hashKey);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.hget(super.prefixKey + key, hashKey);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.hget(super.prefixKey + key, hashKey);
            }
        }
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        if (isClusterMode) {
            return jedisCluster.hset(super.prefixKey + key, hashKey, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.hset(super.prefixKey + key, hashKey, value);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.hset(super.prefixKey + key, hashKey, value);
            }
        }
    }

    @Override
    public Map<String, Object> hgetall(String key) {
        if (isClusterMode) {
            return (Map<String, Object>) (Map) jedisCluster.hgetAll(super.prefixKey + key);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return (Map<String, Object>) (Map) jedis.hgetAll(super.prefixKey + key);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return (Map<String, Object>) (Map) jedis.hgetAll(super.prefixKey + key);
            }
        }
    }
}
