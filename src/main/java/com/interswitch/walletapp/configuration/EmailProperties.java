package com.interswitch.walletapp.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String from
) {
}
