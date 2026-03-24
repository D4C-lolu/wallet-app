package com.interswitch.walletapp.models.request;

import com.interswitch.walletapp.models.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank Long merchantId,
        @NotNull AccountType accountType,
        @NotBlank @Size(min = 3, max = 3) String currency
) {}

