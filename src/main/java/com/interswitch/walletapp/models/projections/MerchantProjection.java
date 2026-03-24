package com.interswitch.walletapp.models.projections;

import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.enums.UserStatus;

import java.time.OffsetDateTime;

public interface MerchantProjection {

    Long getId();

    String getAddress();

    KycStatus getKycStatus();

    MerchantStatus getMerchantStatus();

    MerchantTier getTier();

    Long getUserId();

    String getUserFirstname();

    String getUserLastname();

    String getUserEmail();

    String getUserPhone();

    UserStatus getUserStatus();

    OffsetDateTime getCreatedAt();

    OffsetDateTime getUpdatedAt();
}