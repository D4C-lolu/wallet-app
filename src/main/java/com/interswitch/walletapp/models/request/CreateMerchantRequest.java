package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateMerchantRequest(
        @NotNull Long userId,
        String address
) {}

