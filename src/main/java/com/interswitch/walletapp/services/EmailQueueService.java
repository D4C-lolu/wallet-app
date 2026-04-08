package com.interswitch.walletapp.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.interswitch.walletapp.models.dto.EmailMessage;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailQueueService {

    private final Cache<String, EmailMessage> emailQueue;
    private final GmailService gmailService;

    @Value("${email.max-retries:3}")
    private int maxRetries;

    @Value("${email.batch-size:50}")
    private int batchSize;

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    @PostConstruct
    public void init() {
        if (!emailEnabled) {
            log.info("Email service is DISABLED. No emails will be queued or sent.");
        }
    }

    public boolean isEnabled() {
        return emailEnabled;
    }

    public String queueEmail(String to, String subject, String body) {
        return queueEmail(to, null, null, subject, body, false);
    }

    public String queueEmail(String to, String subject, String body, boolean isHtml) {
        return queueEmail(to, null, null, subject, body, isHtml);
    }

    public String queueEmail(String to, List<String> cc, List<String> bcc, String subject, String body, boolean isHtml) {
        if (!emailEnabled) {
            log.debug("Email disabled - skipping queue for: {}, subject: {}", to, subject);
            return null;
        }

        String id = UUID.randomUUID().toString();
        EmailMessage message = new EmailMessage(id, to, cc, bcc, subject, body, isHtml, OffsetDateTime.now(), 0);
        emailQueue.put(id, message);
        log.info("Email queued with id: {}, to: {}, subject: {}", id, to, subject);
        return id;
    }

    @Scheduled(fixedDelayString = "${email.process-interval-ms:5000}")
    public void processQueue() {
        if (!emailEnabled || !gmailService.isConfigured()) {
            return;
        }

        ConcurrentMap<String, EmailMessage> map = emailQueue.asMap();
        if (map.isEmpty()) {
            return;
        }

        log.debug("Processing email queue. Size: {}", map.size());

        map.entrySet().stream()
                .limit(batchSize)
                .forEach(entry -> {
                    String id = entry.getKey();
                    EmailMessage message = entry.getValue();

                    try {
                        boolean sent = gmailService.sendEmail(message);
                        if (sent) {
                            emailQueue.invalidate(id);
                            log.info("Email sent and removed from queue: {}", id);
                        }
                    } catch (MessagingException | IOException e) {
                        log.error("Failed to send email: {}", id, e);
                        handleFailure(id, message);
                    }
                });
    }

    private void handleFailure(String id, EmailMessage message) {
        if (message.retryCount() >= maxRetries) {
            log.error("Email exceeded max retries, removing from queue: {}", id);
            emailQueue.invalidate(id);
            return;
        }

        EmailMessage updated = message.withIncrementedRetry();
        emailQueue.put(id, updated);
        log.warn("Email retry scheduled. Id: {}, attempt: {}", id, updated.retryCount());
    }

    public int getQueueSize() {
        return (int) emailQueue.estimatedSize();
    }

    public boolean removeFromQueue(String id) {
        if (emailQueue.getIfPresent(id) != null) {
            emailQueue.invalidate(id);
            return true;
        }
        return false;
    }
}
