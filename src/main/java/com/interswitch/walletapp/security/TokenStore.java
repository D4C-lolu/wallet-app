package com.interswitch.walletapp.security;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

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

    public void revokeAllUserTokensBatch(Collection<Long> userIds) {
        if (userIds.isEmpty()) return;
        long now = System.currentTimeMillis();
        Map<Long, Long> batch = userIds.stream()
                .collect(Collectors.toMap(id -> id, _ -> now));
        userRevocationCache.putAll(batch);
    }

    public boolean isRevokedForUser(Long userId, long tokenIssuedAt) {
        Long revokedAt = userRevocationCache.getIfPresent(userId);
        // Token is revoked if it was issued strictly before the revocation time.
        // We use < (not <=) so tokens issued in the same millisecond as revocation are valid.
        return revokedAt != null && tokenIssuedAt < revokedAt;
    }

    /**
     * Clears all caches. Used for testing to prevent cache state leaking between tests.
     */
    public void clearAll() {
        accessTokenCache.invalidateAll();
        refreshTokenCache.invalidateAll();
        userRevocationCache.invalidateAll();
    }
}