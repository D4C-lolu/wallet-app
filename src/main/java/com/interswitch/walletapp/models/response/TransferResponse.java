package com.interswitch.walletapp.models.response;

import com.interswitch.walletapp.models.enums.TransactionType;
import com.interswitch.walletapp.models.enums.TransferStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferResponse(
        Long id,
        String reference,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus transferStatus,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

