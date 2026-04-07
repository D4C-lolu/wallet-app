package com.interswitch.walletapp.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkPermissionRequest(
        @NotEmpty List<@NotNull Long> permissionIds
) {}