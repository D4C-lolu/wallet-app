package com.interswitch.walletapp.services.auth;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.models.request.LoginRequest;
import com.interswitch.walletapp.models.response.AuthResponse;
import com.interswitch.walletapp.services.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Auth Login Integration Tests")
public class AuthLoginIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest("testadmin@verveguard.com", "Admin123!");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("should fail login with wrong password")
    void shouldFailLoginWithWrongPassword() {
        LoginRequest request = new LoginRequest("testadmin@verveguard.com", "WrongPassword!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("should fail login with non existent email")
    void shouldFailLoginWithNonExistentEmail() {
        LoginRequest request = new LoginRequest("nobody@verveguard.com", "Admin123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("should fail login for suspended user")
    void shouldFailLoginForSuspendedUser() {
        LoginRequest request = new LoginRequest("suspended@verveguard.com", "Admin123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("should fail login for inactive user")
    void shouldFailLoginForInactiveUser() {
        LoginRequest request = new LoginRequest("deleted@verveguard.com", "Admin123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("should return different tokens on subsequent logins")
    void shouldReturnDifferentTokensOnSubsequentLogins() {
        LoginRequest request = new LoginRequest("testadmin@verveguard.com", "Admin123!");

        AuthResponse first  = authService.login(request);
        AuthResponse second = authService.login(request);

        assertThat(first.accessToken()).isNotEqualTo(second.accessToken());
        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
    }
}