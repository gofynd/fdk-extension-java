package com.fynd.extension.storage;


import org.springframework.util.StringUtils;
import redis.clients.jedis.*;
import java.util.Map;

public class RedisStorage implements Storage {

    private boolean isClusterMode;
    private JedisPool jedisPool;
    private JedisCluster jedisCluster;
    private String prefixKey;
    private JedisSentinelPool jedisSentinelPool;

    public RedisStorage(JedisPool jedisPool, String prefixKey) {
        this.prefixKey = StringUtils.hasText(prefixKey) ? prefixKey + ":" : "";
        this.jedisPool = jedisPool;
        this.isClusterMode = false;
    }

    public RedisStorage(JedisCluster jedisCluster, String prefixKey) {
        this.prefixKey = StringUtils.hasText(prefixKey) ? prefixKey + ":" : "";
        this.jedisCluster = jedisCluster;
        this.isClusterMode = true;
    }

    public RedisStorage(JedisSentinelPool jedisSentinelPool, String prefixKey) {
        this.prefixKey = StringUtils.hasText(prefixKey) ? prefixKey + ":" : "";
        this.jedisSentinelPool = jedisSentinelPool;
        this.isClusterMode = false;
    }

    @Override
    public String get(String key) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return jedisCluster.get(fullKey);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.get(fullKey);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(fullKey);
            }
        }
    }

    @Override
    public String set(String key, String value) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return jedisCluster.set(fullKey, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.set(fullKey, value);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.set(fullKey, value);
            }
        }
    }

    @Override
    public Long del(String key) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return jedisCluster.del(fullKey);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.del(fullKey);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.del(fullKey);
            }
        }
    }

    @Override
    public String setex(String key, int ttl, String value) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return jedisCluster.setex(fullKey, ttl, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.setex(fullKey, ttl, value);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.setex(fullKey, ttl, value);
            }
        }
    }

    @Override
    public String hget(String key, String hashKey) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return jedisCluster.hget(fullKey, hashKey);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.hget(fullKey, hashKey);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.hget(fullKey, hashKey);
            }
        }
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return jedisCluster.hset(fullKey, hashKey, value);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return jedis.hset(fullKey, hashKey, value);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.hset(fullKey, hashKey, value);
            }
        }
    }

    @Override
    public Map<String, Object> hgetall(String key) {
        String fullKey = prefixKey + key;
        if (isClusterMode) {
            return (Map<String, Object>) (Map) jedisCluster.hgetAll(fullKey);
        } else if (jedisSentinelPool != null) {
            try (Jedis jedis = jedisSentinelPool.getResource()) {
                return (Map<String, Object>) (Map) jedis.hgetAll(fullKey);
            }
        }else {
            try (Jedis jedis = jedisPool.getResource()) {
                return (Map<String, Object>) (Map) jedis.hgetAll(fullKey);
            }
        }
    }
}
