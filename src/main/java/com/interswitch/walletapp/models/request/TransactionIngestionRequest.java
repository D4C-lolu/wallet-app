package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransactionIngestionRequest(
        @NotNull Long accountId,
        @NotNull Long merchantId,
        @NotNull BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {}