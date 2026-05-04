package com.interswitch.walletapp.fraud;

import java.math.BigDecimal;

public record FraudDataSnapshot(
        boolean isBlacklisted,
        boolean isRateLimited,
        int velocityCount,
        BigDecimal transactionLimit,
        Long merchantId
) {}
