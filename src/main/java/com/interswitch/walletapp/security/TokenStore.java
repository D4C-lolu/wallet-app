package com.interswitch.walletapp.security;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenStore {

    private final Cache<String, Long> accessTokenCache;
    private final Cache<String, Long> refreshTokenCache;
    private final Cache<Long, Long> userRevocationCache;

    public void revokeAccessToken(Long userId, String jti) {
        accessTokenCache.put("access:" + userId + ":" + jti, userId);
    }

    public void revokeRefreshToken(Long userId, String jti) {
        refreshTokenCache.put("refresh:" + userId + ":" + jti, userId);
    }

    public boolean isAccessTokenRevoked(Long userId, String jti) {
        return accessTokenCache.getIfPresent("access:" + userId + ":" + jti) != null;
    }

    public boolean isRefreshTokenRevoked(Long userId, String jti) {
        return refreshTokenCache.getIfPresent("refresh:" + userId + ":" + jti) != null;
    }

    public void revokeAllUserTokens(Long userId) {
        userRevocationCache.put(userId, System.currentTimeMillis());
    }

    public boolean isRevokedForUser(Long userId, long tokenIssuedAt) {
        Long revokedAt = userRevocationCache.getIfPresent(userId);
        return revokedAt != null && tokenIssuedAt < revokedAt;
    }
}