package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;

public record CreateMerchantRequest(
        @NotBlank Long userId,
        String address
) {}

