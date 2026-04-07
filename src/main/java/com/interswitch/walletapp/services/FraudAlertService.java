package com.interswitch.walletapp.services;

import com.interswitch.walletapp.dao.FraudAlertDao;
import com.interswitch.walletapp.models.projections.CardOwnerInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAlertService {

    private final EmailQueueService emailQueueService;
    private final FraudAlertDao fraudAlertDao;

    private static final String SUBJECT = "[ALERT] Your card has been blocked - Action Required";

    private static final String BODY_TEMPLATE = """
        Dear Merchant,

        Your card ending in %s has been blocked due to suspected fraudulent activity.

        Card Details:
        - Card Number: %s
        - Card Type: %s
        - Scheme: %s
        - Blocked At: %s

        If you believe this is a mistake, please contact our support team immediately.

        Regards,
        Wallet App Security Team
        """;

    public void notifyCardBlockedForFraud(Long cardId) {
        fraudAlertDao.getCardOwnerInfo(cardId).ifPresentOrElse(
                info -> {
                    String body = buildEmailBody(info);
                    emailQueueService.enqueue(info.email(), SUBJECT, body);
                    log.info("Fraud alert queued for card {} to {}", cardId, info.email());
                },
                () -> log.warn("Could not find owner info for card {}, skipping fraud alert", cardId)
        );
    }

    private String buildEmailBody(CardOwnerInfo info) {
        String lastFour = info.cardNumber().substring(info.cardNumber().length() - 4);
        String blockedAt = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        return BODY_TEMPLATE.formatted(
                lastFour,
                info.cardNumber(),
                info.cardType(),
                info.scheme(),
                blockedAt
        );
    }
}
