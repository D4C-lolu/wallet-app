package com.interswitch.walletapp.context;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.interswitch.walletapp.dao.BlacklistDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class BlacklistCache {

    private final BlacklistDao blacklistDao;

    private final Cache<Long, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public boolean isBlacklisted(Long merchantId) {
        return cache.get(merchantId, blacklistDao::isActivelyBlacklisted);
    }

    public void evict(Long merchantId) {
        cache.invalidate(merchantId);
    }

    public CacheStats stats() {
        return cache.stats();
    }
}