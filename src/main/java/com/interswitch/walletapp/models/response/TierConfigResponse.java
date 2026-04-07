package com.interswitch.walletapp.models.response;

import com.interswitch.walletapp.models.enums.MerchantTier;

import java.math.BigDecimal;

public record TierConfigResponse(
        Long id,
        MerchantTier tier,
        BigDecimal dailyTransactionLimit,
        BigDecimal singleTransactionLimit,
        BigDecimal monthlyTransactionLimit,
        Integer maxCards,
        Integer maxAccounts
) {}
