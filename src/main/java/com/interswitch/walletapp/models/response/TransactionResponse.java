package com.interswitch.walletapp.models.response;

import com.interswitch.walletapp.models.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        Long accountId,
        Long cardId,
        Long transferId,
        TransactionType transactionType,
        String channel,
        BigDecimal amount,
        BigDecimal fee,
        String currency,
        String transactionStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
