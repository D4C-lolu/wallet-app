package com.interswitch.walletapp.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private final Cache<String, Bucket> buckets;

    //TODO: make configurable
    private static final int MAX_REQUESTS = 5;
    private static final int WINDOW_MINUTES = 1;

    private static final Set<String> BYPASS_IPS = Set.of(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",
            "::1",
            "localhost"
    );

    public RateLimiterService() {
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .build();
    }

    public boolean isRateLimited(String ip) {
        if (ip == null || BYPASS_IPS.contains(ip) || isDockerNetwork(ip)) {
            return false;
        }
        Bucket bucket = buckets.get(ip, _ -> buildBucket());
        return !bucket.tryConsume(1);
    }

    private boolean isDockerNetwork(String ip) {
        // Docker bridge networks: 172.16.0.0 - 172.31.255.255
        // Also common: 10.x.x.x, 192.168.x.x (private networks)
        return ip.startsWith("172.") || ip.startsWith("10.") || ip.startsWith("192.168.");
    }

    private Bucket buildBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(MAX_REQUESTS)
                        .refillGreedy(MAX_REQUESTS, Duration.ofMinutes(WINDOW_MINUTES))
                        .build())
                .build();
    }
}