package com.interswitch.walletapp.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.interswitch.walletapp.models.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailQueueService {

    private final Cache<String, EmailMessage> emailQueueCache;

    public void enqueue(String to, String subject, String body) {
        String messageId = UUID.randomUUID().toString();
        EmailMessage message = new EmailMessage(to, subject, body, Instant.now(), 0);
        emailQueueCache.put(messageId, message);
        log.info("Email queued: id={}, to={}, subject={}", messageId, to, subject);
    }

    public Map<String, EmailMessage> getAllPending() {
        return emailQueueCache.asMap();
    }

    public void remove(String messageId) {
        emailQueueCache.invalidate(messageId);
    }

    public void update(String messageId, EmailMessage message) {
        emailQueueCache.put(messageId, message);
    }
}
