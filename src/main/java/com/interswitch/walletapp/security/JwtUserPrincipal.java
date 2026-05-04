package com.interswitch.walletapp.security;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lightweight UserDetails implementation built from JWT claims.
 * No database access required.
 */
public record JwtUserPrincipal(
        Long userId,
        String email,
        String role,
        List<String> permissions
) implements UserDetails {


    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(
                Stream.of(new SimpleGrantedAuthority("ROLE_" + role)),
                permissions.stream().map(SimpleGrantedAuthority::new)
        ).toList();
    }

    @Override
    public String getPassword() {
        return null; // Not available from JWT
    }

    @Override
    public @NonNull String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Assume valid if token is valid
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true; // Assume enabled if token is valid
    }

    public Long getId() {
        return userId;
    }
}
