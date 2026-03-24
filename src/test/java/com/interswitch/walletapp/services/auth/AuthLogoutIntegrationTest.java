package com.interswitch.walletapp.services.auth;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.exceptions.InvalidTokenException;
import com.interswitch.walletapp.models.request.LoginRequest;
import com.interswitch.walletapp.models.response.AuthResponse;
import com.interswitch.walletapp.services.AuthService;
import com.interswitch.walletapp.services.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

@DisplayName("Auth Logout Integration Tests")
public class AuthLogoutIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    @Test
    @DisplayName("should logout successfully")
    void shouldLogoutSuccessfully() {
        AuthResponse loginResponse = authService.login(
                new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        assertThatNoException().isThrownBy(() ->
                authService.logout(loginResponse.accessToken(), loginResponse.refreshToken()));
    }

    @Test
    @DisplayName("should invalidate access token after logout")
    void shouldInvalidateAccessTokenAfterLogout() {
        AuthResponse loginResponse = authService.login(
                new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        authService.logout(loginResponse.accessToken(), loginResponse.refreshToken());

        assertThat(tokenService.isAccessTokenValid(loginResponse.accessToken())).isFalse();
    }

    @Test
    @DisplayName("should invalidate refresh token after logout")
    void shouldInvalidateRefreshTokenAfterLogout() {
        AuthResponse loginResponse = authService.login(
                new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        authService.logout(loginResponse.accessToken(), loginResponse.refreshToken());

        assertThatThrownBy(() -> authService.refresh(loginResponse.refreshToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("should allow login again after logout")
    void shouldAllowLoginAgainAfterLogout() {
        AuthResponse firstLogin = authService.login(
                new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        authService.logout(firstLogin.accessToken(), firstLogin.refreshToken());

        AuthResponse secondLogin = authService.login(
                new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        assertThat(secondLogin.accessToken()).isNotBlank();
        assertThat(secondLogin.accessToken()).isNotEqualTo(firstLogin.accessToken());
    }
}
