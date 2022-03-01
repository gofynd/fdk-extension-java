package com.fynd.extension.storage;

import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Map;

@NoArgsConstructor
public abstract class BaseStorage {

    String prefixKey;

    public BaseStorage(String prefixKey) {
        if (!StringUtils.isEmpty(prefixKey)) {
            this.prefixKey = prefixKey + ":";
        } else {
            this.prefixKey = "";
        }
    }

    public String get(String key) {
        throw new RuntimeException("Method not implemented");
    }

    public String set(String key, String value) {
        throw new RuntimeException("Method not implemented");
    }

    public Object del(String key) {
        throw new RuntimeException("Method not implemented");
    }

    public String setex(String key, int ttl, String value) {
        throw new RuntimeException("Method not implemented");
    }

    public String hget(String key, String hashKey) {
        throw new RuntimeException("Method not implemented");
    }

    public Object hset(String key, String hashKey, String value) {
        throw new RuntimeException("Method not implemented");
    }

    public Map<String, Object> hgetall(String key) {
        throw new RuntimeException("Method not implemented");
    }
}
