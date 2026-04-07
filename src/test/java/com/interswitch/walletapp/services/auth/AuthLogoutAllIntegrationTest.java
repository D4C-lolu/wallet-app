package com.interswitch.walletapp.services.auth;


import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.request.LoginRequest;
import com.interswitch.walletapp.models.response.AuthResponse;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import com.interswitch.walletapp.services.AuthService;
import com.interswitch.walletapp.services.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Auth Logout All Integration Tests")
public class AuthLogoutAllIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setupSecurityContext() {
        User user = userRepository.findByEmail("testadmin@verveguard.com")
                .orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(user), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should revoke all sessions for user")
    void shouldRevokeAllSessionsForUser() {
        AuthResponse firstSession  = authService.login(new LoginRequest("testadmin@verveguard.com", "Admin123!"));
        AuthResponse secondSession = authService.login(new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        authService.logoutAll();

        assertThat(tokenService.isAccessTokenValid(firstSession.accessToken())).isFalse();
        assertThat(tokenService.isAccessTokenValid(secondSession.accessToken())).isFalse();
    }

    @Test
    @DisplayName("should still allow login after logout all")
    void shouldAllowLoginAfterLogoutAll() {
        authService.login(new LoginRequest("testadmin@verveguard.com", "Admin123!"));

        authService.logoutAll();

        AuthResponse newSession = authService.login(new LoginRequest("testadmin@verveguard.com", "Admin123!"));
        assertThat(newSession.accessToken()).isNotBlank();
    }
}
