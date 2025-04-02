package com.fynd.extension.storage;


import redis.clients.jedis.*;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Map;
import java.util.Set;

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
        } else {
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
        } else {
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
        } else {
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
        } else {
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
        } else {
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
        } else {
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
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                return (Map<String, Object>) (Map) jedis.hgetAll(super.prefixKey + key);
            }
        }
    }

    // Add TTL method
    public Long getTTL(String key) {
        if (isClusterMode) {
            return jedisCluster.ttl(super.prefixKey + key);
        } else {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.ttl(super.prefixKey + key);
            }
        }
    }


    public String getFirstKey(String keyPattern) {
        String pattern = super.prefixKey + keyPattern;

        if (isClusterMode) {
            return getFirstKeyFromCluster(pattern);  // Cluster Mode
        } else if (jedisSentinelPool != null) {
            return getFirstKeyFromSentinel(pattern); // Sentinel Mode
        } else {
            return getFirstKeyFromStandalone(pattern); // Standalone Mode
        }
    }

    private String getFirstKeyFromStandalone(String pattern) {
        try (Jedis jedis = jedisPool.getResource()) {
            String cursor = "0";
            ScanParams scanParams = new ScanParams().match(pattern).count(100);

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                if (!scanResult.getResult().isEmpty()) {
                    return scanResult.getResult().get(0); // Return first key found
                }
                cursor = scanResult.getCursor();
            } while (!cursor.equals("0")); // Stop once first match is found
        }
        return null; // No match found
    }

    private String getFirstKeyFromSentinel(String pattern) {
        try (Jedis jedis = jedisSentinelPool.getResource()) {
            String cursor = "0";
            ScanParams scanParams = new ScanParams().match(pattern).count(100);

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                if (!scanResult.getResult().isEmpty()) {
                    return scanResult.getResult().get(0); // Return first key found
                }
                cursor = scanResult.getCursor();
            } while (!cursor.equals("0")); // Stop once first match is found
        }
        return null; // No match found
    }

    private String getFirstKeyFromCluster(String pattern) {
                String cursor = "0";
                ScanParams scanParams = new ScanParams().match(pattern).count(100);
                do {
                    ScanResult<String> scanResult = jedisCluster.scan(cursor, scanParams);
                    if (!scanResult.getResult().isEmpty()) {
                        return scanResult.getResult().get(0); // Return first key found
                    }
                    cursor = scanResult.getCursor();
                } while (!cursor.equals("0")); // Stop once first match is found
        return null; // No match found
    }

//    public Set<String> getKeys(String key) {
//        if (isClusterMode) {
//            return jedisCluster.keys(super.prefixKey + key);
//        } else {
//            try (Jedis jedis = jedisPool.getResource()) {
//                return jedis.keys(super.prefixKey + key);
//            }
//        }
//    }
}
