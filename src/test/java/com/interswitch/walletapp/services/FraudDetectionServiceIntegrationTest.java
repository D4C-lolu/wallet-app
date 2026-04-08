package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import com.interswitch.walletapp.models.response.FraudAttemptResponse;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
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

import static org.assertj.core.api.Assertions.assertThat;

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
        String hammeredIp = "10.0.0.1";
        FraudEvaluationContext ctx = new FraudEvaluationContext(
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
        FraudEvaluationContext ctx = buildContext(cardNumber, new BigDecimal("100.00"));

        // Hit the threshold exactly, the next call should trip the flag
        for (int i = 0; i < 3; i++) {
            fraudDetectionService.evaluate(ctx);
        }

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
    @DisplayName("should flag SUSPICIOUS for amount that is a multiple of 1000")
    void shouldFlagSuspiciousForRoundAmount() {
        FraudEvaluationContext ctx = buildContext("4111111111111115", new BigDecimal("5000.00"));

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("should not flag amount that is not a multiple of 1000")
    void shouldNotFlagNonRoundAmount() {
        FraudEvaluationContext ctx = buildContext("4111111111111116", new BigDecimal("1500.50"));

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("should flag SUSPICIOUS for transaction before business hours (hour 3)")
    void shouldFlagSuspiciousForTransactionBeforeBusinessHours() {
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000",
                new BigDecimal("350.00"),
                "NGN",
                "4111111111111117",
                "192.168.1.1",
                OffsetDateTime.now().withHour(3)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("should flag SUSPICIOUS for transaction after business hours (hour 23)")
    void shouldFlagSuspiciousForTransactionAfterBusinessHours() {
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000",
                new BigDecimal("350.00"),
                "NGN",
                "4111111111111118",
                "192.168.1.1",
                OffsetDateTime.now().withHour(23)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }

    @Test
    @DisplayName("should not flag after-hours at opening boundary (hour 6)")
    void shouldNotFlagAfterHoursAtOpeningBoundary() {
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000",
                new BigDecimal("350.00"),
                "NGN",
                "4111111111111119",
                "192.168.1.1",
                OffsetDateTime.now().withHour(6)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }


    @Test
    @DisplayName("should flag SUSPICIOUS when amount exceeds merchant single transaction limit")
    void shouldFlagSuspiciousWhenAmountExceedsMerchantSingleLimit() {
        // merchant 1L must be seeded with a single-transaction limit below this amount
        FraudEvaluationContext ctx = buildContext("4111111111111120", new BigDecimal("9999999.00"));

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }


    @Test
    @DisplayName("should remain SUSPICIOUS (not escalate) when multiple soft flags fire together")
    void shouldRemainSuspiciousWithMultipleSoftFlags() {
        // round amount + after hours
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000",
                new BigDecimal("3000.00"),
                "NGN",
                "4111111111111121",
                "192.168.1.1",
                OffsetDateTime.now().withHour(2)
        );

        FraudStatus status = fraudDetectionService.evaluate(ctx);

        assertThat(status).isEqualTo(FraudStatus.SUSPICIOUS);
    }


    @Test
    @DisplayName("should persist a fraud attempt log entry after each evaluation")
    void shouldPersistFraudAttemptAfterEvaluation() {
        Page<FraudAttemptResponse> before = fraudDetectionService.getFraudAttempts(1, 100);

        fraudDetectionService.evaluate(buildContext("4111111111111122", new BigDecimal("350.00")));

        Page<FraudAttemptResponse> after = fraudDetectionService.getFraudAttempts(1, 100);
        assertThat(after.getTotalElements()).isGreaterThan(before.getTotalElements());
    }

    @Test
    @DisplayName("should get fraud attempts paginated")
    void shouldGetFraudAttemptsPaginated() {
        fraudDetectionService.evaluate(buildContext("4111111111111123", new BigDecimal("350.00")));

        Page<FraudAttemptResponse> page = fraudDetectionService.getFraudAttempts(1, 10);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
    }
}