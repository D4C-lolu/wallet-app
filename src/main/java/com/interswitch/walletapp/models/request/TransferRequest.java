package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Size(min = 10, max = 10) String fromAccountNumber,
        @NotBlank @Size(min = 10, max = 10) String toAccountNumber,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        String description,
        @NotBlank String cardNumber
) {}