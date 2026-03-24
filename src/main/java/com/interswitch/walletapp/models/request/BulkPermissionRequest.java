package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkPermissionRequest(
        @NotEmpty List<@NotBlank Long> permissionIds
) {}