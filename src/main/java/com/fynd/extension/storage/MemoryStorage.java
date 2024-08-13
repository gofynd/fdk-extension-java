package com.fynd.extension.storage;

import java.util.HashMap;
import java.util.Map;

public class MemoryStorage extends BaseStorage {

    private String prefixKey;

    private Map<String, Object> data = new HashMap<>();

    public MemoryStorage(String prefixKey) {
        super(prefixKey);

    }

    @Override
    public String get(String key) {
        return (String) data.get(this.prefixKey + key);
    }

    @Override
    public String set(String key, String value) {
        return (String) data.put(this.prefixKey + key, value);
    }

    @Override
    public Object del(String key) {
        return data.remove(this.prefixKey + key);
    }

    @Override
    public String setex(String key, int ttl, String value) {
        return (String) data.put(this.prefixKey + key, value);
    }

    @Override
    public String hget(String key, String hashKey) {
        Map<String, Object> hashMap = (HashMap<String, Object>) data.get(this.prefixKey + key);
        return (String) hashMap.get(hashKey);
    }

    @Override
    public Object hset(String key, String hashKey, String value) {
        Map<String, Object> hashMap = (HashMap<String, Object>) data.get(this.prefixKey + key);
        hashMap.put(hashKey, value);
        return data.put(this.prefixKey + key, hashMap);
    }

    @Override
    public Map<String, Object> hgetall(String key) {
        return (Map) data.get(this.prefixKey + key);
    }
}
