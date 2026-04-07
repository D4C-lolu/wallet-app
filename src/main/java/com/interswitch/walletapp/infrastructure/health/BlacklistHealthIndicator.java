package com.interswitch.walletapp.infrastructure.health;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.interswitch.walletapp.context.BlacklistCache;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BlacklistHealthIndicator implements HealthIndicator {

    private final BlacklistCache blacklistCache;

    @Override
    public Health health() {
        CacheStats stats = blacklistCache.stats();
        double hitRate = stats.hitRate();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("hitRate", String.format("%.2f%%", hitRate * 100));
        details.put("hitCount", stats.hitCount());
        details.put("missCount", stats.missCount());
        details.put("loadCount", stats.loadCount());

        if (hitRate >= 0.5 || stats.requestCount() == 0) {
            return Health.up().withDetails(details).build();
        }

        return Health.down()
                .withDetails(details)
                .withDetail("reason", "Blacklist cache hit rate below 50%")
                .build();
    }
}