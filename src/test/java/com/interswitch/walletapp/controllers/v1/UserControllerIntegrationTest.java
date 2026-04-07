package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.repositories.RoleRepository;
import com.interswitch.walletapp.repositories.UserRepository;

import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.request.ChangePasswordRequest;
import com.interswitch.walletapp.models.request.CreateUserRequest;
import com.interswitch.walletapp.models.request.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("User Controller Integration Tests")
public class UserControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Long adminRoleId;
    private Long testAdminUserId;

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        merchantToken   = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");
        adminRoleId     = roleRepository.findByName("ADMIN").orElseThrow().getId();
        testAdminUserId = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("should create user successfully as super admin")
    void shouldCreateUserSuccessfullyAsSuperAdmin() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                "john.doe@test.com", "55555555555",
                "Admin123!", adminRoleId
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(request.email()))
                .andExpect(jsonPath("$.data.firstname").value(request.firstname()))
                .andExpect(jsonPath("$.data.lastname").value(request.lastname()));
    }

    @Test
    @DisplayName("should return 403 when merchant tries to create user")
    void shouldReturn403WhenMerchantTriesToCreateUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                "john.doe2@test.com", "55555555556",
                "Admin123!", adminRoleId
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearerToken(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                "john.doe3@test.com", "55555555557",
                "Admin123!", adminRoleId
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 400 with invalid request body")
    void shouldReturn400WithInvalidRequestBody() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "", "", null, "notanemail", "", "Admin123!", null
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should get all users successfully")
    void shouldGetAllUsersSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to get all users")
    void shouldReturn403WhenMerchantTriesToGetAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearerToken(merchantToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should get user by id successfully")
    void shouldGetUserByIdSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", testAdminUserId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(testAdminUserId))
                .andExpect(jsonPath("$.data.email").value("testadmin@verveguard.com"));
    }

    @Test
    @DisplayName("should return 404 for non existent user")
    void shouldReturn404ForNonExistentUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should update user successfully")
    void shouldUpdateUserSuccessfully() throws Exception {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        UpdateUserRequest request = new UpdateUserRequest(
                "Updated", "Name", null, existing.getPhone(), existing.getEmail()
        );

        mockMvc.perform(put("/api/v1/users/{userId}", testAdminUserId)
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstname").value(request.firstname()))
                .andExpect(jsonPath("$.data.lastname").value(request.lastname()));
    }

    @Test
    @DisplayName("should change user status successfully")
    void shouldChangeUserStatusSuccessfully() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{userId}/status", testAdminUserId)
                        .header("Authorization", bearerToken(superAdminToken))
                        .param("status", UserStatus.SUSPENDED.name()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should change user role successfully")
    void shouldChangeUserRoleSuccessfully() throws Exception {
        Long merchantRoleId = roleRepository.findByName(Roles.USER).orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/users/{userId}/role", testAdminUserId)
                        .header("Authorization", bearerToken(superAdminToken))
                        .param("roleId", String.valueOf(merchantRoleId)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should change password successfully")
    void shouldChangePasswordSuccessfully() throws Exception {
        String testAdminToken = loginAndGetAccessToken("testadmin@verveguard.com", "Admin123!");
        ChangePasswordRequest request = new ChangePasswordRequest(
                "Admin123!", "NewPassword123!", "NewPassword123!"
        );

        mockMvc.perform(patch("/api/v1/users/{userId}/password", testAdminUserId)
                        .header("Authorization", bearerToken(testAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to delete user")
    void shouldReturn403WhenMerchantTriesToDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}", testAdminUserId)
                        .header("Authorization", bearerToken(merchantToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should delete user successfully as super admin")
    void shouldDeleteUserSuccessfullyAsSuperAdmin() throws Exception {
        Long toDeleteId = userRepository.findByEmail("testmerchant@verveguard.com").orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/users/{userId}", toDeleteId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNoContent());
    }
}