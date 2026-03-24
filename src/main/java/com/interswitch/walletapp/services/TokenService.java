package com.interswitch.walletapp.services;

import com.interswitch.walletapp.exceptions.InvalidTokenException;
import com.interswitch.walletapp.models.response.AuthResponse;
import com.interswitch.walletapp.security.TokenStore;
import com.interswitch.walletapp.security.UserDetailsServiceImpl;
import com.interswitch.walletapp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenStore tokenStore;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public AuthResponse issueTokens(UserPrincipal principal) {
        String accessToken  = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);
        return new AuthResponse(accessToken, refreshToken);
    }

    public AuthResponse refresh(String refreshToken) {
        if (jwtService.isTokenInvalid(refreshToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        Long userId = jwtService.extractUserId(refreshToken);
        String jti    = jwtService.extractJti(refreshToken);

        if (tokenStore.isRefreshTokenRevoked(userId, jti)) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        // rotate — revoke old refresh token
        tokenStore.revokeRefreshToken(userId, jti);

        // load user and issue new pair
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserById(userId);
        return issueTokens(principal);
    }

    public void revoke(String accessToken, String refreshToken) {
        Long userId    = jwtService.extractUserId(accessToken);
        String accessJti = jwtService.extractJti(accessToken);
        String refreshJti = jwtService.extractJti(refreshToken);
        tokenStore.revokeAccessToken(userId, accessJti);
        tokenStore.revokeRefreshToken(userId, refreshJti);
    }

    public void revokeAll(Long userId) {
        tokenStore.revokeAllUserTokens(userId);
    }

    public boolean isAccessTokenValid(String token) {
        if (jwtService.isTokenInvalid(token)) return false;
        Long userId = jwtService.extractUserId(token);
        String jti    = jwtService.extractJti(token);
        long issuedAt = jwtService.extractIssuedAt(token).getTime();
        return !tokenStore.isAccessTokenRevoked(userId, jti)
                && !tokenStore.isRevokedForUser(userId, issuedAt);
    }
}
