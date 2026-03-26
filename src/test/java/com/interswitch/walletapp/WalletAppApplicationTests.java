package com.interswitch.walletapp;

import com.interswitch.walletapp.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public class WalletAppApplicationTests {

    @Test
    void contextLoads() {
    }

}


