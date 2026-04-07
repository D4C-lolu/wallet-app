package com.interswitch.walletapp.models.response;

import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        List<PermissionResponse> permissions
) {}
