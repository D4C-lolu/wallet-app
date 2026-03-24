package com.interswitch.walletapp.constants;

public final class Permissions {
    public static final String USER_READ         = "user:read";
    public static final String USER_CREATE       = "user:create";
    public static final String USER_UPDATE       = "user:update";
    public static final String USER_DELETE       = "user:delete";
    public static final String MERCHANT_READ     = "merchant:read";
    public static final String MERCHANT_CREATE   = "merchant:create";
    public static final String MERCHANT_UPDATE   = "merchant:update";
    public static final String MERCHANT_DELETE   = "merchant:delete";
    public static final String MERCHANT_KYC      = "merchant:kyc";
    public static final String MERCHANT_BLACKLIST= "merchant:blacklist";
    public static final String ACCOUNT_READ      = "account:read";
    public static final String ACCOUNT_CREATE    = "account:create";
    public static final String ACCOUNT_UPDATE    = "account:update";
    public static final String ACCOUNT_DELETE    = "account:delete";
    public static final String CARD_READ         = "card:read";
    public static final String CARD_CREATE       = "card:create";
    public static final String CARD_UPDATE       = "card:update";
    public static final String CARD_DELETE       = "card:delete";
    public static final String CARD_BLOCK        = "card:block";
    public static final String TRANSACTION_READ  = "transaction:read";
    public static final String TRANSACTION_CREATE= "transaction:create";
    public static final String TRANSACTION_REVERSE="transaction:reverse";
    public static final String TRANSFER_READ     = "transfer:read";
    public static final String TRANSFER_CREATE   = "transfer:create";
    public static final String TRANSFER_REVERSE  = "transfer:reverse";
    public static final String TIER_READ         = "tier:read";
    public static final String TIER_UPDATE       = "tier:update";
    public static final String ROLE_READ         = "role:read";
    public static final String ROLE_CREATE       = "role:create";
    public static final String ROLE_UPDATE       = "role:update";
    public static final String ROLE_DELETE       = "role:delete";
    public static final String PERMISSION_READ   = "permission:read";
    public static final String PERMISSION_ASSIGN = "permission:assign";

    private Permissions() {}
}