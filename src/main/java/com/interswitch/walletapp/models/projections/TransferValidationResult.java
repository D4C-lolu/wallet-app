package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;

public record TransferValidationResult(
    int referenceExists,
    BigDecimal fromBalance,
    String fromCurrency,
    String fromStatus,
    String toCurrency,
    String toStatus,
    BigDecimal singleLimit,
    BigDecimal dailyLimit,
    BigDecimal monthlyLimit
) {}