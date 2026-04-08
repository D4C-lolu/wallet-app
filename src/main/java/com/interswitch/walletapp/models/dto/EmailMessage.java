package com.interswitch.walletapp.models.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EmailMessage(
        String id,
        String to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String body,
        boolean isHtml,
        OffsetDateTime createdAt,
        int retryCount
) {
    public EmailMessage withIncrementedRetry() {
        return new EmailMessage(id, to, cc, bcc, subject, body, isHtml, createdAt, retryCount + 1);
    }
}
