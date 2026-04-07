package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.annotation.ValidSortField;
import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.models.request.*;
import com.interswitch.walletapp.models.response.RoleResponse;
import com.interswitch.walletapp.services.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Role Management", description = "Endpoints for managing user roles and their associated permission mappings")
@RestController
@RequestMapping("roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(
            summary = "Create Role",
            description = "Define a new system role. Requires ROLE_CREATE authority."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_CREATE + "')")
    public RoleResponse createRole(@RequestBody @Valid CreateRoleRequest request) {
        return roleService.createRole(request);
    }

    @Operation(
            summary = "List Roles (Paginated)",
            description = "Retrieve a paginated list of roles with custom sorting. Requires ROLE_READ authority."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_READ + "')")
    public Page<RoleResponse> getAllRoles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @ValidSortField(target = RoleResponse.class) @RequestParam(defaultValue = "name") String sortField,
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection
    ) {
        return roleService.getAllRoles(page, size, sortField, sortDirection);
    }

    @Operation(
            summary = "Get Role by ID",
            description = "Fetch specific role details including linked permissions. Requires ROLE_READ authority."
    )
    @GetMapping("{roleId}")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_READ + "')")
    public RoleResponse getRoleById(@PathVariable Long roleId) {
        return roleService.getRoleById(roleId);
    }

    @Operation(
            summary = "Bulk Assign Permissions",
            description = "Link multiple permissions to a role simultaneously. Requires PERMISSION_ASSIGN authority."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("{roleId}/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.PERMISSION_ASSIGN + "')")
    public void assignPermissions(
            @PathVariable Long roleId,
            @RequestBody @Valid BulkPermissionRequest request
    ) {
        roleService.assignPermissions(roleId, request.permissionIds());
    }

    @Operation(
            summary = "Bulk Revoke Permissions",
            description = "Remove multiple permissions from a role in one request. Requires PERMISSION_ASSIGN authority."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("{roleId}/permissions")
    @PreAuthorize("hasAuthority('" + Permissions.PERMISSION_ASSIGN + "')")
    public void revokePermissions(
            @PathVariable Long roleId,
            @RequestBody @Valid BulkPermissionRequest request
    ) {
        roleService.revokePermissions(roleId, request.permissionIds());
    }

    @Operation(
            summary = "List All Roles (Unpaginated)",
            description = "Get a simple list of all roles. Useful for dropdowns. Requires ROLE_READ authority."
    )
    @GetMapping("/all") // Note: Added '/all' to avoid path conflict with the paginated GET
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_READ + "')")
    public List<RoleResponse> getAllRoles() {
        return roleService.getAllRoles();
    }

    @Operation(
            summary = "Assign Single Permission",
            description = "Grant a specific permission to a role. Requires PERMISSION_ASSIGN authority."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('" + Permissions.PERMISSION_ASSIGN + "')")
    public void assignPermission(
            @PathVariable Long roleId,
            @PathVariable Long permissionId
    ) {
        roleService.assignPermission(roleId, permissionId);
    }

    @Operation(
            summary = "Revoke Single Permission",
            description = "Remove a specific permission from a role. Requires PERMISSION_ASSIGN authority."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('" + Permissions.PERMISSION_ASSIGN + "')")
    public void revokePermission(
            @PathVariable Long roleId,
            @PathVariable Long permissionId
    ) {
        roleService.revokePermission(roleId, permissionId);
    }
}