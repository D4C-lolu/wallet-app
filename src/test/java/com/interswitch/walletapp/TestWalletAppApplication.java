package com.interswitch.walletapp;

import com.interswitch.walletapp.config.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestWalletAppApplication {

    public static void main(String[] args) {
        SpringApplication.from(WalletAppApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
