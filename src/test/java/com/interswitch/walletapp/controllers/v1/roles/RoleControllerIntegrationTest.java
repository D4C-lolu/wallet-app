package com.interswitch.walletapp.controllers.v1.roles;


import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.constants.Permissions;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.models.request.*;
import com.interswitch.walletapp.repositories.PermissionRepository;
import com.interswitch.walletapp.repositories.RoleRepository;

import com.interswitch.walletapp.entities.Role;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Role Controller Integration Tests")
public class RoleControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    @DisplayName("should create role successfully as super admin")
    void shouldCreateRoleSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        CreateRoleRequest request = new CreateRoleRequest("NEW_ROLE");

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(request.name()));
    }

    @Test
    @DisplayName("should return 409 when role already exists")
    void shouldReturn409WhenRoleAlreadyExists() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Role existing = roleRepository.findByName("ADMIN").orElseThrow();
        CreateRoleRequest request = new CreateRoleRequest(existing.getName());

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to create role")
    void shouldReturn403WhenMerchantTriesToCreateRole() throws Exception {
        String token = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");
        CreateRoleRequest request = new CreateRoleRequest("NEW_ROLE_2");

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest("NEW_ROLE_3");

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should get all roles successfully")
    void shouldGetAllRolesSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to get all roles")
    void shouldReturn403WhenMerchantTriesToGetAllRoles() throws Exception {
        String token = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should get role by id successfully")
    void shouldGetRoleByIdSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Role existing = roleRepository.findByName("ADMIN").orElseThrow();

        mockMvc.perform(get("/api/v1/roles/{roleId}", existing.getId())
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(existing.getId()))
                .andExpect(jsonPath("$.data.name").value(existing.getName()));
    }

    @Test
    @DisplayName("should return 404 for non existent role")
    void shouldReturn404ForNonExistentRole() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/roles/{roleId}", 9999L)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should assign permissions to role successfully")
    void shouldAssignPermissionsToRoleSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        Permission permission = permissionRepository.findByName("tier:update").orElseThrow();

        BulkPermissionRequest request = new BulkPermissionRequest(List.of(permission.getId()));

        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions", role.getId())
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 403 when admin tries to assign permissions")
    void shouldReturn403WhenAdminTriesToAssignPermissions() throws Exception {
        String token = loginAndGetAccessToken("testadmin@verveguard.com", "Admin123!");
        Role role = roleRepository.findByName(Roles.USER).orElseThrow();
        Permission permission = permissionRepository.findByName("tier:read").orElseThrow();

        BulkPermissionRequest request = new BulkPermissionRequest(List.of(permission.getId()));

        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions", role.getId())
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should revoke permissions from role successfully")
    void shouldRevokePermissionsFromRoleSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Role role = roleRepository.findByName(Roles.ADMIN).orElseThrow();
        Permission permission = permissionRepository.findByName(Permissions.TIER_READ).orElseThrow();

        BulkPermissionRequest request = new BulkPermissionRequest(List.of(permission.getId()));

        mockMvc.perform(delete("/api/v1/roles/{roleId}/permissions", role.getId())
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 400 with empty permission ids")
    void shouldReturn400WithEmptyPermissionIds() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Role role = roleRepository.findByName("ADMIN").orElseThrow();

        BulkPermissionRequest request = new BulkPermissionRequest(List.of());

        mockMvc.perform(post("/api/v1/roles/{roleId}/permissions", role.getId())
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}