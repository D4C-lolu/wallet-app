package com.interswitch.walletapp.cron;

import com.interswitch.walletapp.models.EmailMessage;
import com.interswitch.walletapp.services.EmailQueueService;
import com.interswitch.walletapp.services.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSenderJob {

    private static final int MAX_RETRIES = 3;

    private final EmailQueueService emailQueueService;
    private final EmailSenderService emailSenderService;

    @Scheduled(fixedRate = 30000)
    public void processEmailQueue() {
        Map<String, EmailMessage> pending = emailQueueService.getAllPending();

        if (pending.isEmpty()) {
            return;
        }

        log.info("Processing {} pending emails", pending.size());

        for (Map.Entry<String, EmailMessage> entry : pending.entrySet()) {
            String messageId = entry.getKey();
            EmailMessage message = entry.getValue();

            boolean success = emailSenderService.send(message);

            if (success) {
                emailQueueService.remove(messageId);
            } else if (message.retryCount() >= MAX_RETRIES) {
                log.error("Email to {} failed after {} retries, discarding", message.to(), MAX_RETRIES);
                emailQueueService.remove(messageId);
            } else {
                emailQueueService.update(messageId, message.withIncrementedRetry());
                log.warn("Email to {} failed, retry {}/{}", message.to(), message.retryCount() + 1, MAX_RETRIES);
            }
        }
    }
}
