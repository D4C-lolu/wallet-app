package com.interswitch.walletapp.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final JwtProperties jwtProperties;

    @Bean
    public Cache<String, Long> accessTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(jwtProperties.getAccessTokenExpiry(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Bean
    public Cache<String, Long> refreshTokenCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(jwtProperties.getRefreshTokenExpiry(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Bean
    public Cache<Long, Long> userRevocationCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(jwtProperties.getRefreshTokenExpiry(), TimeUnit.SECONDS)
                .build();
    }
}