package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;

public record TransactionLimitSummary(
    BigDecimal totalAmount
) {}