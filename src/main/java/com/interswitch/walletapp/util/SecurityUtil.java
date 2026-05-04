package com.interswitch.walletapp.util;

import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.UnauthorizedException;
import com.interswitch.walletapp.security.JwtUserPrincipal;
import com.interswitch.walletapp.security.UserPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtil {

    public static Optional<Long> findCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof JwtUserPrincipal jwtPrincipal) {
            return Optional.of(jwtPrincipal.getId());
        }

        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal.getId());
        }

        return Optional.empty();
    }

    public static Long getCurrentUserId() {
        return findCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    public static String extractToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new BadRequestException("Invalid authorization header format");
        }

        String token = bearerToken.stripLeading();

        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).stripLeading();
        }

        if (token.isBlank()) {
            throw new BadRequestException("Invalid authorization header format");
        }

        return token;
    }
}
