package com.interswitch.walletapp.base;

import com.interswitch.walletapp.config.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class BaseIntegrationTest {

    protected String uniqueIp() {
        int counter = (int) (System.nanoTime() % 900000) + 100000;
        return "10.0." + (counter / 1000) + "." + (counter % 255);
    }

    @Autowired
    @PersistenceContext
    private EntityManager entityManager;

    protected void forceFlush() {
        entityManager.flush();
        entityManager.clear();
    }
}
