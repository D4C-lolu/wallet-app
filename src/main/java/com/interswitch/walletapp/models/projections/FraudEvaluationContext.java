package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FraudEvaluationContext(
        String transactionId,
        String accountNumber,
        BigDecimal amount,
        String currency,
        String cardNumber,
        String ipAddress,
        OffsetDateTime transactionTime
) {}

