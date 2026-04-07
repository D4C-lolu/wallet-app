package com.interswitch.walletapp.models.response;

import com.interswitch.walletapp.models.enums.AccountStatus;
import com.interswitch.walletapp.models.enums.AccountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        Long merchantId,
        String accountNumber,
        AccountType accountType,
        String currency,
        BigDecimal balance,
        BigDecimal ledgerBalance,
        AccountStatus accountStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
