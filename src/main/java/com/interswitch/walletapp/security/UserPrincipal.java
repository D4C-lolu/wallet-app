package com.interswitch.walletapp.security;

import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.enums.UserStatus;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record UserPrincipal(User user) implements UserDetails {

    public Long getId() {
        return user.getId();
    }

    @Override
    @NonNull
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
        user.getRole().getPermissions().forEach(p ->
                authorities.add(new SimpleGrantedAuthority(p.getName()))
        );
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isNotDeleted() && user.getUserStatus() != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isEnabled() {
        return user.isNotDeleted() && user.getUserStatus() == UserStatus.ACTIVE;
    }
}
