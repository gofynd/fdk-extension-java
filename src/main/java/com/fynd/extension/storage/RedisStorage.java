package com.fynd.extension.storage;


import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

public class RedisStorage extends BaseStorage {

    private JedisPool pool;

    private String prefixKey;

    public RedisStorage(JedisPool pool, String prefixKey) {
        super(prefixKey);
        this.pool = pool;
    }

    @Override
    public String get(String key) {

        try (Jedis client = pool.getResource()) {
            return client.get(super.prefixKey + key);
        }
    }

    @Override
    public String set(String key, String value) {
        try (Jedis client = pool.getResource()) {
            return client.set(super.prefixKey + key, value);
        }
    }

    @Override
    public Long del(String key) {
        try (Jedis client = pool.getResource()) {
            return client.del(super.prefixKey + key);
        }
    }

    @Override
    public String setex(String key, int ttl, String value) {
        try (Jedis client = pool.getResource()) {
            return client.setex(super.prefixKey + key, ttl, value);
        }
    }

    @Override
    public String hget(String key, String hashKey) {
        try (Jedis client = pool.getResource()) {
            return client.hget(super.prefixKey + key, hashKey);
        }
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        try (Jedis client = pool.getResource()) {
            return client.hset(super.prefixKey + key, hashKey, value);
        }
    }

    @Override
    public Map<String, Object> hgetall(String key) {
        try (Jedis client = pool.getResource()) {
            return (Map<String, Object>) (Map) client.hgetAll(super.prefixKey + key);
        }
    }
}
