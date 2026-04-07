package com.interswitch.walletapp.util;

import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.UnauthorizedException;
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

        if (!(authentication.getPrincipal() instanceof UserPrincipal(User user))) {
            return Optional.empty();
        }

        return Optional.of(user.getId());
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
