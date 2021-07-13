package com.fynd.extension.storage;


import redis.clients.jedis.Jedis;

import java.util.Map;

public class RedisStorage extends BaseStorage{

    private Jedis client;
    private String prefixKey;

    public RedisStorage(Jedis client, String prefixKey) {
        super(prefixKey);
        this.client = client;
    }

    @Override
    public String get(String key) {
        return client.get(this.prefixKey + key);
    }

    @Override
    public String set(String key,String value) {
        return client.set(this.prefixKey + key,value);
    }

    @Override
    public Long del(String key) {
        return client.del(this.prefixKey + key);
    }

    @Override
    public String setex(String key,int ttl,String value) {
        return client.setex(this.prefixKey + key,ttl,value);
    }

    @Override
    public String hget(String key,String hashKey) {
        return client.hget(this.prefixKey + key,hashKey);
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        return client.hset(this.prefixKey + key,hashKey,value);
    }

    @Override
    public Map<String,Object> hgetall(String key) {
        return (Map)client.hgetAll(this.prefixKey + key);
    }
}
