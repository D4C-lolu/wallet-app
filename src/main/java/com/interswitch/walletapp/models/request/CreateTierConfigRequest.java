package com.interswitch.walletapp.models.request;

import com.interswitch.walletapp.models.enums.MerchantTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTierConfigRequest(
        @NotNull MerchantTier tier,
        @NotNull @Positive BigDecimal dailyTransactionLimit,
        @NotNull @Positive BigDecimal singleTransactionLimit,
        @NotNull @Positive BigDecimal monthlyTransactionLimit,
        @NotNull @Positive Integer maxCards,
        @NotNull @Positive Integer maxAccounts
) {}

