package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FraudEvaluationContext(
        Long merchantId,
        BigDecimal amount,
        String currency,
        String cardNumber,
        String ipAddress,
        OffsetDateTime transactionTime
) {}

