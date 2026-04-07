package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;

public record CreatePermissionRequest(
        @NotBlank String name,
        String description
) {}
