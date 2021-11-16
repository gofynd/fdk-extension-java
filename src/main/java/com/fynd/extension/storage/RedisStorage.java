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

        String result = client.get(this.prefixKey + key);
        client.close();
        return result;
    }

    @Override
    public String set(String key,String value) {
        String result = client.set(this.prefixKey + key,value);
        client.close();
        return result;
    }

    @Override
    public Long del(String key) {
        Long result =client.del(this.prefixKey + key);
        client.close();
        return result;
    }

    @Override
    public String setex(String key,int ttl,String value) {
        String result = client.setex(this.prefixKey + key,ttl,value);
        client.close();
        return result;
    }

    @Override
    public String hget(String key,String hashKey) {
        String result =client.hget(this.prefixKey + key,hashKey);
        client.close();
        return result;
    }

    @Override
    public Long hset(String key, String hashKey, String value) {
        Long result = client.hset(this.prefixKey + key,hashKey,value);
        client.close();
        return result;
    }

    @Override
    public Map<String,Object> hgetall(String key) {
        Map<String,Object> result =(Map)client.hgetAll(this.prefixKey + key);
        client.close();
        return result;
    }
}
