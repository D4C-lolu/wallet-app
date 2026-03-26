package com.interswitch.walletapp.models.projections;

import com.interswitch.walletapp.models.enums.FraudStatus;

import java.math.BigDecimal;
import java.util.List;

public record FraudAttemptRecord(
        String cardHash,
        Long merchantId,
        String ipAddress,
        BigDecimal amount,
        String currency,
        FraudStatus status,
        List<String> flags
) {}
