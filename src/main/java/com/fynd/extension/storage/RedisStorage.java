package com.fynd.extension.storage;


import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

public class RedisStorage extends BaseStorage{

    private JedisPool pool;
    private String prefixKey;

    public RedisStorage(JedisPool pool, String prefixKey) {
        super(prefixKey);
        this.pool = pool;
    }

    @Override
    public String get(String key) {

        try(Jedis client = pool.getResource()) {
            String result = client.get(this.prefixKey + key);
            return result;
        }
    }

    @Override
    public String set(String key,String value) {
        try(Jedis client = pool.getResource()) {
            String result = client.set(this.prefixKey + key, value);
            return result;
        }
    }

    @Override
    public Long del(String key) {
        try(Jedis client = pool.getResource()) {
            Long result = client.del(this.prefixKey + key);
            return result;
        }
    }

    @Override
    public String setex(String key,int ttl,String value) {
        try(Jedis client = pool.getResource()) {
            String result = client.setex(this.prefixKey + key, ttl, value);
            return result;
        }
    }

    @Override
    public String hget(String key,String hashKey) {
        try(Jedis client = pool.getResource()) {
            String result = client.hget(this.prefixKey + key, hashKey);
            return result;
        }
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        try(Jedis client = pool.getResource()) {
            Long result = client.hset(this.prefixKey + key, hashKey, value);
            return result;
        }
    }

    @Override
    public Map<String,Object> hgetall(String key) {
        try(Jedis client = pool.getResource()) {
            Map<String, Object> result = (Map) client.hgetAll(this.prefixKey + key);
            return result;
        }
    }
}
