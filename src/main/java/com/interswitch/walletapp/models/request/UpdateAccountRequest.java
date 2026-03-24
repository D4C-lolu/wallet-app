package com.interswitch.walletapp.models.request;

import com.interswitch.walletapp.models.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(
        @NotNull AccountStatus accountStatus
) {}
