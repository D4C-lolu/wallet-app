package com.interswitch.walletapp.fraud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserIpHistoryService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "fraud:ip:";
    private static final int MAX_IPS = 10;
    private static final Duration TTL = Duration.ofDays(30);

    public Set<String> getRecentIps(String accountId) {
        if (accountId == null) {
            return Set.of();
        }
        try {
            String key = buildKey(accountId);
            List<String> ips = redisTemplate.opsForList().range(key, 0, MAX_IPS - 1);
            return ips != null ? new HashSet<>(ips) : Set.of();
        } catch (Exception e) {
            log.warn("Failed to get IP history for account: {}", accountId, e);
            return Set.of();
        }
    }

    public void recordIp(String accountId, String ipAddress) {
        if (accountId == null || ipAddress == null) {
            return;
        }
        try {
            String key = buildKey(accountId);
            redisTemplate.opsForList().remove(key, 1, ipAddress);
            redisTemplate.opsForList().leftPush(key, ipAddress);
            redisTemplate.opsForList().trim(key, 0, MAX_IPS - 1);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Failed to record IP {} for account: {}", ipAddress, accountId, e);
        }
    }

    @Async
    public void recordIpAsync(String accountId, String ipAddress) {
        recordIp(accountId, ipAddress);
    }

    private String buildKey(String accountId) {
        return KEY_PREFIX + accountId;
    }
}
