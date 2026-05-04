package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.dao.FraudDao;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Fraud Detection Integration Tests")
public class FraudControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private FraudDao fraudDao;

    private String superAdminToken;

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
    }

    private FraudEvaluationContext buildContext(String accountNumber, BigDecimal amount) {
        return new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                accountNumber,
                amount,
                "NGN",
                "4111111111111111",
                uniqueIp(),
                OffsetDateTime.now()
        );
    }

    @Test
    @DisplayName("should return CLEAN for a normal transaction")
    void shouldReturnCleanForNormalTransaction() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now().withHour(10)
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("CLEAN"));
    }

    @Test
    @DisplayName("should return BLOCKED for blacklisted merchant")
    void shouldReturnBlockedForBlacklistedMerchant() throws Exception {
        String ip = uniqueIp();
        String blacklistedAccountNumber = "2200000001"; // belongs to blacklisted merchant 2
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                blacklistedAccountNumber, new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now().withHour(10)
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("BLOCKED"));
    }

    @Test
    @DisplayName("should return BLOCKED for rate limited IP")
    void shouldReturnBlockedForRateLimitedIp() throws Exception {
        String ip = "203.0.113.1";

        for (int i = 0; i < 5; i++) {
            FraudEvaluationContext ctx = new FraudEvaluationContext(
                    java.util.UUID.randomUUID().toString(),
                    "0000000000", new BigDecimal("100.00"), "NGN",
                    "4111111111111111", ip, OffsetDateTime.now().withHour(10)
            );
            mockMvc.perform(post("/api/v1/fraud/evaluate")
                    .header("Authorization", bearerToken(superAdminToken))
                    .with(r -> { r.setRemoteAddr(ip); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ctx)));
        }

        // 6th should be blocked
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("100.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now().withHour(10)
        );
        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("BLOCKED"));
    }

    @Test
    @DisplayName("should return CLEAN for over-limit alone (25 pts < 30 review threshold)")
    void shouldReturnCleanForOverLimitAlone() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("9999999.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now().withHour(10)
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("CLEAN"));
    }

    @Test
    @DisplayName("should return SUSPICIOUS for over-limit + after-hours (25+10=35 >= 30)")
    void shouldReturnSuspiciousForOverLimitWithAfterHours() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("9999999.00"), "NGN",
                "4111111111111199", ip,
                OffsetDateTime.now().withHour(2)
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("SUSPICIOUS"));
    }

    @Test
    @DisplayName("should return SUSPICIOUS for card velocity exceeded (30 pts >= 30 review)")
    void shouldReturnSuspiciousForCardVelocityExceeded() throws Exception {
        String ip = uniqueIp();
        String cardNumber = "4111111111111112";

        // 3 prior attempts — >= 4 means the 4th call trips the velocity flag
        for (int i = 0; i < 3; i++) {
            FraudEvaluationContext warmup = new FraudEvaluationContext(
                    java.util.UUID.randomUUID().toString(),
                    "0000000000", new BigDecimal("100.00"), "NGN",
                    cardNumber, ip, OffsetDateTime.now().withHour(10)
            );
            mockMvc.perform(post("/api/v1/fraud/evaluate")
                    .header("Authorization", bearerToken(superAdminToken))
                    .with(r -> { r.setRemoteAddr(ip); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(warmup)));
        }

        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("100.00"), "NGN",
                cardNumber, ip, OffsetDateTime.now().withHour(10)
        );
        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("SUSPICIOUS"));
    }

    @Test
    @DisplayName("should persist fraud attempt to database")
    void shouldPersistFraudAttemptToDatabase() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now().withHour(10)
        );

        long before = fraudDao.countFraudAttempts();

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                .header("Authorization", bearerToken(superAdminToken))
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ctx)));

        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(fraudDao.countFraudAttempts()).isGreaterThan(before)
                );
    }

    @Test
    @DisplayName("should require authentication on evaluate endpoint")
    void shouldRequireAuthOnEvaluate() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                java.util.UUID.randomUUID().toString(),
                "0000000000", new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now().withHour(10)
        );

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should return paginated fraud attempts for super admin")
    void shouldReturnPaginatedFraudAttempts() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/attempts")
                        .header("Authorization", bearerToken(superAdminToken))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page.totalElements").isNumber());
    }

    @Test
    @DisplayName("should deny access to fraud attempts for non super admin")
    void shouldDenyFraudAttemptsForNonSuperAdmin() throws Exception {
        String merchantToken = loginAndGetAccessToken("demo.merchant@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/fraud/attempts")
                        .header("Authorization", bearerToken(merchantToken)))
                .andExpect(status().isForbidden());
    }
}