package com.interswitch.walletapp.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class IdempotencyStore {

    private final Cache<String, Object> cache;

    public IdempotencyStore() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    public Object get(String key, Supplier<Object> loader) {
        return cache.get(key, k -> loader.get());
    }
}