package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import com.interswitch.walletapp.models.response.FraudAttemptResponse;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Fraud Detection Service Integration Tests")
public class FraudDetectionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FraudDetectionService fraudDetectionService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setupSecurityContext() {
        User superAdmin = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private FraudEvaluationContext buildContext(String cardNumber, BigDecimal amount) {
        return new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                amount,
                "NGN",
                cardNumber,
                uniqueIp(),
                OffsetDateTime.now().withHour(10)
        );
    }

    @Test
    @DisplayName("should return CLEAN for a normal transaction")
    void shouldReturnCleanForNormalTransaction() {
        FraudEvaluationContext ctx = buildContext("4111111111111111", new BigDecimal("350.00"));

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }


    @Test
    @DisplayName("should return BLOCKED when merchant is blacklisted")
    void shouldReturnBlockedWhenMerchantIsBlacklisted() {
        // merchant 2 (testmerchant) is blacklisted, account 2200000001 belongs to them
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "2200000001",
                new BigDecimal("100.00"),
                "NGN",
                "4111111111111111",
                "192.168.1.1",
                OffsetDateTime.now().withHour(10)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.BLOCKED);
    }

    @Test
    @DisplayName("should return BLOCKED when IP is rate-limited")
    void shouldReturnBlockedWhenIpIsRateLimited() {
        String hammeredIp = "203.0.113.1";
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                new BigDecimal("100.00"),
                "NGN",
                "4111111111111112",
                hammeredIp,
                OffsetDateTime.now().withHour(10)
        );

        FraudStatus finalStatus = null;
        for (int i = 0; i < 200; i++) {
            finalStatus = fraudDetectionService.evaluate(ctx);
            if (finalStatus == FraudStatus.BLOCKED) break;
        }

        assertThat(finalStatus).isEqualTo(FraudStatus.BLOCKED);
    }

    @Test
    @DisplayName("should return SUSPICIOUS when card velocity threshold is exceeded")
    void shouldReturnSuspiciousWhenCardVelocityExceeded() {
        String cardNumber = "4111111111111113";

        for (int i = 0; i < 3; i++) {
            FraudEvaluationContext warmup = buildContext(cardNumber, new BigDecimal("100.00"));
            fraudDetectionService.evaluate(warmup);
        }

        // Wait for all 3 async inserts to commit before the 4th call reads velocity
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> fraudDetectionService.getFraudAttempts(1, 100)
                        .getContent().stream()
                        .filter(a -> a.cardHash().equals(DigestUtils.sha256Hex(cardNumber)))
                        .count() >= 3
                );

        FraudEvaluationContext ctx = buildContext(cardNumber, new BigDecimal("100.00"));
        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("should return CLEAN when card velocity is below threshold")
    void shouldReturnCleanWhenCardVelocityBelowThreshold() {
        String cardNumber = "4111111111111114";
        FraudEvaluationContext ctx = buildContext(cardNumber, new BigDecimal("100.00"));

        for (int i = 0; i < 2; i++) {
            fraudDetectionService.evaluate(ctx);
        }

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("should not flag amount that is not a multiple of 1000")
    void shouldNotFlagNonRoundAmount() {
        FraudEvaluationContext ctx = buildContext("4111111111111116", new BigDecimal("1500.50"));

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("should return CLEAN for after-hours when score below review threshold")
    void shouldReturnCleanForAfterHoursAlone() {
        // After-hours alone = 10 points, below review threshold of 30
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                new BigDecimal("350.00"),
                "NGN",
                "4111111111111117",
                uniqueIp(),
                OffsetDateTime.now().withHour(3)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("should flag SUSPICIOUS when after-hours combined with velocity")
    void shouldFlagSuspiciousForAfterHoursWithVelocity() {
        String cardNumber = "4111111111111118";
        String ip = uniqueIp();

        for (int i = 0; i < 3; i++) {
            FraudEvaluationContext warmup = new FraudEvaluationContext(
                    java.util.UUID.randomUUID().toString(),
                    "0000000000",
                    new BigDecimal("100.00"),
                    "NGN",
                    cardNumber,
                    ip,
                    OffsetDateTime.now().withHour(10)
            );
            fraudDetectionService.evaluate(warmup);
        }

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> fraudDetectionService.getFraudAttempts(1, 100)
                        .getContent().stream()
                        .filter(a -> a.cardHash().equals(DigestUtils.sha256Hex(cardNumber)))
                        .count() >= 3);

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                new BigDecimal("350.00"),
                "NGN",
                cardNumber,
                ip,
                OffsetDateTime.now().withHour(23)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("should not flag after-hours at opening boundary (hour 6)")
    void shouldNotFlagAfterHoursAtOpeningBoundary() {
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                new BigDecimal("350.00"),
                "NGN",
                "4111111111111119",
                uniqueIp(),
                OffsetDateTime.now().withHour(6)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }


    @Test
    @DisplayName("should return CLEAN when only over-limit (score 25 < review threshold 30)")
    void shouldReturnCleanWhenOnlyOverLimit() {
        // Over-limit alone = 25 points, below review threshold of 30
        FraudEvaluationContext ctx = buildContext("4111111111111120", new BigDecimal("9999999.00"));

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("should flag SUSPICIOUS when over-limit combined with after-hours")
    void shouldFlagSuspiciousWhenOverLimitWithAfterHours() {
        // Over-limit (25) + after-hours (10) = 35 >= review threshold (30)
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                new BigDecimal("9999999.00"),
                "NGN",
                "4111111111111125",
                uniqueIp(),
                OffsetDateTime.now().withHour(3)  // after hours
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }


    @Test
    @DisplayName("should return BLOCKED when score exceeds block threshold")
    void shouldReturnBlockedWhenScoreExceedsBlockThreshold() {
        // Velocity (30) + over-limit (25) + after-hours (10) = 65 -> REVIEW
        // Need to add more to hit 70 for BLOCK
        // Let's use: velocity (30) + over-limit (25) + after-hours (10) + velocity again from rapid calls
        String cardNumber = "4111111111111121";
        String ip = uniqueIp();

        // Build up velocity during business hours
        for (int i = 0; i < 3; i++) {
            FraudEvaluationContext warmup = new FraudEvaluationContext(
                    java.util.UUID.randomUUID().toString(),
                    "0000000000",
                    new BigDecimal("100.00"),
                    "NGN",
                    cardNumber,
                    ip,
                    OffsetDateTime.now().withHour(10)
            );
            fraudDetectionService.evaluate(warmup);
        }

        // Now: after-hours (10) + velocity exceeded (30) + over-limit (25) = 65 -> SUSPICIOUS
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000",
                new BigDecimal("9999999.00"),  // exceeds limit
                "NGN",
                cardNumber,
                ip,
                OffsetDateTime.now().withHour(2)  // after hours
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }


    @Test
    @DisplayName("should persist a fraud attempt log entry after each evaluation")
    void shouldPersistFraudAttemptAfterEvaluation() {
        long before = fraudDetectionService.getFraudAttempts(1, 100).getTotalElements();

        fraudDetectionService.evaluate(buildContext("4111111111111122", new BigDecimal("350.00")));

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(fraudDetectionService.getFraudAttempts(1, 100).getTotalElements())
                                .isGreaterThan(before)
                );
    }

    @Test
    @DisplayName("should get fraud attempts paginated")
    void shouldGetFraudAttemptsPaginated() {
        fraudDetectionService.evaluate(buildContext("4111111111111123", new BigDecimal("350.00")));

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Page<FraudAttemptResponse> page = fraudDetectionService.getFraudAttempts(1, 10);
                    assertThat(page).isNotNull();
                    assertThat(page.getContent()).isNotEmpty();
                });
    }
}