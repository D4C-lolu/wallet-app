package com.interswitch.walletapp.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.interswitch.walletapp.repositories.MerchantBlacklistRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BlacklistCache {

    private final MerchantBlacklistRepository blacklistRepository;
    private final Cache<Long, Boolean> cache;

    public BlacklistCache(MerchantBlacklistRepository blacklistRepository, MeterRegistry meterRegistry) {
        this.blacklistRepository = blacklistRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        CaffeineCacheMetrics.monitor(meterRegistry, cache, "blacklist");
    }

    public boolean isBlacklisted(Long merchantId) {
        return cache.get(merchantId, blacklistRepository::isActivelyBlacklisted);
    }

    public void evict(Long merchantId) {
        cache.invalidate(merchantId);
    }

    public CacheStats stats() {
        return cache.stats();
    }
}