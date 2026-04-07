package com.interswitch.walletapp.controllers.v1.roles;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.models.request.CreatePermissionRequest;
import com.interswitch.walletapp.repositories.PermissionRepository;

import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Permission Controller Integration Tests")
public class PermissionControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    @DisplayName("should create permission successfully as super admin")
    void shouldCreatePermissionSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        CreatePermissionRequest request = new CreatePermissionRequest("new:permission", "A new permission");

        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(request.name()))
                .andExpect(jsonPath("$.data.description").value(request.description()));
    }

    @Test
    @DisplayName("should return 409 when permission already exists")
    void shouldReturn409WhenPermissionAlreadyExists() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Permission existing = permissionRepository.findByName("user:read").orElseThrow();
        CreatePermissionRequest request = new CreatePermissionRequest(existing.getName(), "duplicate");

        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to create permission")
    void shouldReturn403WhenMerchantTriesToCreatePermission() throws Exception {
        String token = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");
        CreatePermissionRequest request = new CreatePermissionRequest("new:permission2", "desc");

        mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreatePermissionRequest request = new CreatePermissionRequest("new:permission3", "desc");

        mockMvc.perform(post("/api/v1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should get all permissions successfully")
    void shouldGetAllPermissionsSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to get all permissions")
    void shouldReturn403WhenMerchantTriesToGetAllPermissions() throws Exception {
        String token = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should get permission by id successfully")
    void shouldGetPermissionByIdSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        Permission existing = permissionRepository.findByName("user:read").orElseThrow();

        mockMvc.perform(get("/api/v1/permissions/{permissionId}", existing.getId())
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(existing.getId()))
                .andExpect(jsonPath("$.data.name").value(existing.getName()));
    }

    @Test
    @DisplayName("should return 404 for non existent permission")
    void shouldReturn404ForNonExistentPermission() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/permissions/{permissionId}", 9999L)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isNotFound());
    }
}