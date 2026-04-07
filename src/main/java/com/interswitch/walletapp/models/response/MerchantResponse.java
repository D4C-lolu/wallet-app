package com.interswitch.walletapp.models.response;

import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.enums.UserStatus;

import java.time.OffsetDateTime;

public record MerchantResponse(
        Long id,
        String address,
        KycStatus kycStatus,
        MerchantStatus merchantStatus,
        MerchantTier tier,
        Long userId,
        String userFirstname,
        String userLastname,
        String userEmail,
        String userPhone,
        UserStatus userStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}