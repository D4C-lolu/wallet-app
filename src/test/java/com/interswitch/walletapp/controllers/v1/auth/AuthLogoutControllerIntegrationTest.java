package com.interswitch.walletapp.controllers.v1.auth;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.models.response.AuthResponse;
import org.junit.jupiter.api.DisplayName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Controller Logout Tests")
public class AuthLogoutControllerIntegrationTest extends BaseControllerIntegrationTest {

    //@Test
    @DisplayName("should logout successfully")
    void shouldLogoutSuccessfully() throws Exception {
        AuthResponse tokens = login("testadmin@verveguard.com", "Admin123!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearerToken(tokens.accessToken()))
                        .header("Refresh-Token", tokens.refreshToken()))
                .andExpect(status().isNoContent());
    }

    //@Test
    @DisplayName("should return 401 with missing authorization header")
    void shouldReturn401WithMissingAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Refresh-Token", "sometoken"))
                .andExpect(status().isUnauthorized());
    }

    //@Test
    @DisplayName("should return 400 with missing refresh token header")
    void shouldReturn400WithMissingRefreshTokenHeader() throws Exception {
        String accessToken = loginAndGetAccessToken("testadmin@verveguard.com", "Admin123!");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearerToken(accessToken)))
                .andExpect(status().isBadRequest());
    }
}