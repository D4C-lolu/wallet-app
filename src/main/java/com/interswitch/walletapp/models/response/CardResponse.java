package com.interswitch.walletapp.models.response;

import com.interswitch.walletapp.models.enums.CardScheme;
import com.interswitch.walletapp.models.enums.CardStatus;
import com.interswitch.walletapp.models.enums.CardType;

import java.time.OffsetDateTime;

public record CardResponse(
        Long id,
        Long accountId,
        String cardNumber,
        CardType cardType,
        CardScheme scheme,
        int expiryMonth,
        int expiryYear,
        CardStatus cardStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
