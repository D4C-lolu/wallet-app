package com.interswitch.walletapp.models.request;

import com.interswitch.walletapp.models.enums.CardStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCardRequest(
        @NotNull CardStatus cardStatus
) {}
