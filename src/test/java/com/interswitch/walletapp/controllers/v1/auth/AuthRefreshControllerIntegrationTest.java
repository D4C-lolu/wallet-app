package com.interswitch.walletapp.controllers.v1.auth;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Controller Refresh Tests")
public class AuthRefreshControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Test
    @DisplayName("should refresh tokens successfully")
    void shouldRefreshTokensSuccessfully() throws Exception {
        String refreshToken = loginAndGetRefreshToken("testadmin@verveguard.com", "Admin123!");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Refresh-Token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("should return 401 with invalid refresh token")
    void shouldReturn401WithInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Refresh-Token", "invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 400 with missing refresh token header")
    void shouldReturn400WithMissingRefreshTokenHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isBadRequest());
    }
}