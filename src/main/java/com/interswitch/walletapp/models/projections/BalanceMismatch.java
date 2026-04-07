package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;

public record BalanceMismatch(
    Long accountId,
    BigDecimal storedBalance,
    BigDecimal calculatedBalance
) {}

