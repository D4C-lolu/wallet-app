package com.interswitch.walletapp.models.projections;

public record MerchantAccountValidation(
            Long merchantId,
            String kycStatus,
            String tier,
            int maxAccounts,
            int currentAccountCount
) {}

