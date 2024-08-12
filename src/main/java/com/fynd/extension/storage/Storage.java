package com.fynd.extension.storage;

import java.util.Map;

public interface Storage {
    String get(String key);
    String set(String key, String value);
    Object del(String key);
    String setex(String key, int ttl, String value);
    String hget(String key, String hashKey);
    Object hset(String key, String hashKey, String value);
    Map<String, Object> hgetall(String key);
}