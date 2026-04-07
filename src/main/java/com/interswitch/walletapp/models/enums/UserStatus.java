package com.interswitch.walletapp.models.enums;

public enum UserStatus {
    ACTIVE,        // normal, can log in
    INACTIVE,      // registered but not yet verified/onboarded
    SUSPENDED,     // temporarily blocked, can be reinstated
    DEACTIVATED    // soft equivalent of deleted, self-initiated
}

