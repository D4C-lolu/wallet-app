package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;

public record TransferValidationResult(
    int referenceExists,
    Long fromAccountId,
    BigDecimal fromBalance,
    String fromCurrency,
    String fromStatus,
    Long toAccountId,
    String toCurrency,
    String toStatus,
    BigDecimal singleLimit,
    BigDecimal dailyLimit,
    BigDecimal monthlyLimit
) {}