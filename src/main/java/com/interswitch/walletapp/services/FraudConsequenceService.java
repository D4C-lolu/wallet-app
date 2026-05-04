package com.interswitch.walletapp.services;

import com.interswitch.walletapp.dao.CardDao;
import com.interswitch.walletapp.dao.MerchantDao;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudConsequenceService {

    private final CardDao cardDao;
    private final MerchantDao merchantDao;
    private final EmailQueueService emailQueueService;

    @Transactional
    public void applyConsequences(FraudEvaluationContext ctx, String cardHash, FraudStatus status, List<String> flags) {
        if (status == FraudStatus.BLOCKED) {
            handleBlockedTransaction(ctx, cardHash, flags);
        } else if (status == FraudStatus.SUSPICIOUS) {
            handleSuspiciousTransaction(ctx, cardHash, flags);
        }
    }

    private void handleBlockedTransaction(FraudEvaluationContext ctx, String cardHash, List<String> flags) {
        boolean cardBlocked = blockCard(cardHash);
        sendFraudAlertEmail(ctx, FraudStatus.BLOCKED, flags, cardBlocked);
    }

    private void handleSuspiciousTransaction(FraudEvaluationContext ctx, String cardHash, List<String> flags) {
        // For suspicious transactions, we don't block the card but we do alert the merchant
        sendFraudAlertEmail(ctx, FraudStatus.SUSPICIOUS, flags, false);
    }

    private boolean blockCard(String cardHash) {
        try {
            boolean blocked = cardDao.blockByHash(cardHash);
            if (blocked) {
                log.warn("Card blocked due to fraud detection. Hash: {}", maskHash(cardHash));
            } else {
                log.debug("Card not blocked (may already be blocked or not found). Hash: {}", maskHash(cardHash));
            }
            return blocked;
        } catch (Exception e) {
            log.error("Failed to block card with hash: {}", maskHash(cardHash), e);
            return false;
        }
    }

    private void sendFraudAlertEmail(FraudEvaluationContext ctx, FraudStatus status, List<String> flags, boolean cardWasBlocked) {
        if (!emailQueueService.isEnabled()) {
            return;
        }

        try {
            String merchantEmail = merchantDao.getEmailByAccountNumber(ctx.accountNumber()).orElse(null);
            String merchantName = merchantDao.getNameByAccountNumber(ctx.accountNumber()).orElse("Merchant");

            if (merchantEmail == null) {
                log.warn("Cannot send fraud alert - merchant email not found for account: {}", ctx.accountNumber());
                return;
            }

            String subject = buildEmailSubject(status);
            String body = buildEmailBody(ctx, status, flags, cardWasBlocked, merchantName);

            emailQueueService.queueEmail(merchantEmail, subject, body, true);
            log.info("Fraud alert email queued for merchant: {}, status: {}", merchantEmail, status);

        } catch (Exception e) {
            log.error("Failed to queue fraud alert email for account: {}", ctx.accountNumber(), e);
        }
    }

    private String buildEmailSubject(FraudStatus status) {
        return switch (status) {
            case BLOCKED -> "[URGENT] Transaction Blocked - Fraud Alert";
            case SUSPICIOUS -> "[WARNING] Suspicious Transaction Detected";
            default -> "Transaction Alert";
        };
    }

    private String buildEmailBody(FraudEvaluationContext ctx, FraudStatus status, List<String> flags, boolean cardWasBlocked, String merchantName) {
        String statusColor = status == FraudStatus.BLOCKED ? "#dc3545" : "#ffc107";
        String statusText = status == FraudStatus.BLOCKED ? "BLOCKED" : "SUSPICIOUS";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background-color: ").append(statusColor).append("; color: white; padding: 20px; text-align: center; }");
        html.append(".content { padding: 20px; background-color: #f9f9f9; }");
        html.append(".details { background-color: white; padding: 15px; margin: 15px 0; border-radius: 5px; }");
        html.append(".flag { display: inline-block; background-color: #e9ecef; padding: 5px 10px; margin: 2px; border-radius: 3px; font-size: 12px; }");
        html.append(".warning { color: #dc3545; font-weight: bold; }");
        html.append(".footer { text-align: center; padding: 20px; font-size: 12px; color: #666; }");
        html.append("</style></head><body>");

        html.append("<div class='container'>");
        html.append("<div class='header'>");
        html.append("<h1>").append(status == FraudStatus.BLOCKED ? "Transaction Blocked" : "Suspicious Activity Detected").append("</h1>");
        html.append("</div>");

        html.append("<div class='content'>");
        html.append("<p>Dear ").append(merchantName).append(",</p>");

        if (status == FraudStatus.BLOCKED) {
            html.append("<p class='warning'>A transaction on your account has been <strong>BLOCKED</strong> due to potential fraudulent activity.</p>");
        } else {
            html.append("<p>A transaction on your account has been flagged as <strong>suspicious</strong>. The transaction was allowed to proceed, but we recommend reviewing your account activity.</p>");
        }

        html.append("<div class='details'>");
        html.append("<h3>Transaction Details</h3>");
        html.append("<p><strong>Account:</strong> ").append(maskAccountNumber(ctx.accountNumber())).append("</p>");
        html.append("<p><strong>Amount:</strong> ").append(ctx.currency()).append(" ").append(ctx.amount()).append("</p>");
        html.append("<p><strong>IP Address:</strong> ").append(ctx.ipAddress()).append("</p>");
        html.append("<p><strong>Time:</strong> ").append(ctx.transactionTime()).append("</p>");
        html.append("<p><strong>Status:</strong> <span style='color:").append(statusColor).append("'>").append(statusText).append("</span></p>");
        html.append("</div>");

        html.append("<div class='details'>");
        html.append("<h3>Triggered Flags</h3>");
        for (String flag : flags) {
            html.append("<span class='flag'>").append(formatFlag(flag)).append("</span> ");
        }
        html.append("</div>");

        if (cardWasBlocked) {
            html.append("<div class='details'>");
            html.append("<h3 class='warning'>Card Action Taken</h3>");
            html.append("<p>The card associated with this transaction has been <strong>automatically blocked</strong> for your protection. ");
            html.append("Please contact support if you believe this was in error.</p>");
            html.append("</div>");
        }

        html.append("<p><strong>What should you do?</strong></p>");
        html.append("<ul>");
        html.append("<li>Review recent transactions on your account</li>");
        html.append("<li>If you don't recognize this activity, contact our support team immediately</li>");
        html.append("<li>Consider updating your account security settings</li>");
        html.append("</ul>");

        html.append("</div>");

        html.append("<div class='footer'>");
        html.append("<p>This is an automated message from VerveguardAPI Fraud Detection System.</p>");
        html.append("<p>If you have questions, please contact support@verveguard.com</p>");
        html.append("</div>");

        html.append("</div>");
        html.append("</body></html>");

        return html.toString();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 6) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String maskHash(String hash) {
        if (hash == null || hash.length() < 8) {
            return "****";
        }
        return hash.substring(0, 8) + "...";
    }

    private String formatFlag(String flag) {
        return flag.replace("_", " ");
    }
}
