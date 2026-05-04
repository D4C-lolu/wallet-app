package com.interswitch.walletapp.controllers.v1.auth;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.models.request.LoginRequest;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Controller Login Tests")
public class AuthLoginControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Test
    @DisplayName("should login successfully and return tokens")
    void shouldLoginSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("testadmin@verveguard.com", "Admin123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("should return 401 with wrong password")
    void shouldReturn401WithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("testadmin@verveguard.com", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorMessage").value("Invalid email or password"));
    }

    @Test
    @DisplayName("should return 401 with non existent email")
    void shouldReturn401WithNonExistentEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@verveguard.com", "Admin123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorMessage").value("Invalid email or password"));
    }

    @Test
    @DisplayName("should return 403 for suspended user")
    void shouldReturn403ForSuspendedUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("suspended@verveguard.com", "Admin123!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorMessage").value("Account is not accessible"));
    }

    @Test
    @DisplayName("should return 403 for inactive user")
    void shouldReturn403ForInactiveUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("deleted@verveguard.com", "Admin123!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorMessage").value("Account is not accessible"));
    }

    @Test
    @DisplayName("should return 400 with blank email")
    void shouldReturn400WithBlankEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("", "Admin123!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 with invalid email format")
    void shouldReturn400WithInvalidEmailFormat() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("notanemail", "Admin123!"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 400 with blank password")
    void shouldReturn400WithBlankPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("testadmin@verveguard.com", ""))))
                .andExpect(status().isBadRequest());
    }
}