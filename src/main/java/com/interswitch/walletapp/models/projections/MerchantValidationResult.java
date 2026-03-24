package com.interswitch.walletapp.models.projections;

public record MerchantValidationResult(
    boolean merchantExists,
    boolean userExists,
    boolean tierExists
) {}