package com.interswitch.walletapp.models.projections;

import java.math.BigDecimal;

public record StuckTransfer(
    Long id,
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount
) {}
