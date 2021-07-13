package com.fynd.extension.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ExtensionContext {

    private static Map<String,Object> extensionThreadLocal = new HashMap<>();

    private ExtensionContext() {}

    public static Map<String,Object> get() {
        return extensionThreadLocal;

    }


    public static <T> T get(String key,Class<T> clazz) {
        Map<String,Object> context = extensionThreadLocal;
        return clazz.cast(context.get(key));
    }



    public static boolean isPresent(String key) {
        Map<String,Object> context = extensionThreadLocal;
        return Optional.of(context.get(key)).isPresent();
    }

    public static void set(String key,Object value) {
        extensionThreadLocal.put(key,value);
    }

    public static void unset(String key) {
        extensionThreadLocal.remove(key);
    }

    public static void clear() {
        extensionThreadLocal.clear();
    }
}
