package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.models.request.CreatePermissionRequest;
import com.interswitch.walletapp.models.response.PermissionResponse;
import com.interswitch.walletapp.services.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Permission Management", description = "Endpoints for defining and retrieving system-level granular permissions")
@RestController
@RequestMapping("permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(
            summary = "Create Permission",
            description = "Register a new granular permission in the system. Requires ROLE_CREATE authority."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_CREATE + "')")
    public PermissionResponse createPermission(@RequestBody @Valid CreatePermissionRequest request) {
        return permissionService.createPermission(request);
    }

    @Operation(
            summary = "List All Permissions",
            description = "Retrieve the full list of all available system permissions. Requires PERMISSION_READ authority."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PERMISSION_READ + "')")
    public List<PermissionResponse> getAllPermissions() {
        return permissionService.getAllPermissions();
    }

    @Operation(
            summary = "Get Permission by ID",
            description = "Fetch details for a specific permission by its unique identifier. Requires PERMISSION_READ authority."
    )
    @GetMapping("{permissionId}")
    @PreAuthorize("hasAuthority('" + Permissions.PERMISSION_READ + "')")
    public PermissionResponse getPermissionById(@PathVariable Long permissionId) {
        return permissionService.getPermissionById(permissionId);
    }
}