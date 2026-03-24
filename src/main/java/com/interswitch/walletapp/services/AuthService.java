package com.interswitch.walletapp.services;

import com.interswitch.walletapp.models.request.LoginRequest;
import com.interswitch.walletapp.models.response.AuthResponse;
import com.interswitch.walletapp.security.UserDetailsServiceImpl;
import com.interswitch.walletapp.security.UserPrincipal;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserDetailsServiceImpl userDetailsService;

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(request.email());
        return tokenService.issueTokens(principal);
    }

    public AuthResponse refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    public void logout(String accessHeader, String refreshToken) {

        String accessToken = SecurityUtil.extractToken(accessHeader);
        tokenService.revoke(accessToken, refreshToken);
    }

    public void logoutAll() {
        Long userId = SecurityUtil.getCurrentUserId();
        tokenService.revokeAll(userId);
    }
   }