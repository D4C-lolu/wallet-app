package com.interswitch.walletapp.controllers.v1.auth;


import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Controller Logout All Tests")
public class AuthLogoutAllControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Test
    @DisplayName("should logout all sessions successfully")
    void shouldLogoutAllSuccessfully() throws Exception {
        String accessToken = loginAndGetAccessToken("testadmin@verveguard.com", "Admin123!");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", bearerToken(accessToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 401 with missing authorization header")
    void shouldReturn401WithMissingAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return 401 with invalid token")
    void shouldReturn401WithInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", bearerToken("invalid.token.here")))
                .andExpect(status().isUnauthorized());
    }
}