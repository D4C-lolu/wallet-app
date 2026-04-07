package com.interswitch.walletapp.configuration;

import com.interswitch.walletapp.util.SecurityUtil;
import lombok.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    @NonNull
    public Optional<Long> getCurrentAuditor() {
        return SecurityUtil.findCurrentUserId();
    }
}