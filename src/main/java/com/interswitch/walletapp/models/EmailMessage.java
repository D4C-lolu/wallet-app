package com.interswitch.walletapp.models;

import java.time.Instant;

public record EmailMessage(
        String to,
        String subject,
        String body,
        Instant createdAt,
        int retryCount
) {
    public EmailMessage withIncrementedRetry() {
        return new EmailMessage(to, subject, body, createdAt, retryCount + 1);
    }
}
