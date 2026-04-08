package com.interswitch.walletapp.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.interswitch.walletapp.models.dto.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class EmailConfig {

    @Value("${email.queue.max-size:10000}")
    private int queueMaxSize;

    @Value("${email.queue.expire-after-write-minutes:30}")
    private int expireAfterWriteMinutes;

    @Bean
    public Cache<String, EmailMessage> emailQueue() {
        return Caffeine.newBuilder()
                .maximumSize(queueMaxSize)
                .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
                .build();
    }
}
