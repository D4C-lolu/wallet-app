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

import static org.assertj.core.api.Assertions.assertThat;
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
                "0000000000", new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
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
                blacklistedAccountNumber, new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
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
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000", new BigDecimal("100.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
        );

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/fraud/evaluate")
                    .header("Authorization", bearerToken(superAdminToken))
                    .with(r -> { r.setRemoteAddr(ip); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ctx)));
        }

        // 6th should be blocked
        mockMvc.perform(post("/api/v1/fraud/evaluate")
                        .header("Authorization", bearerToken(superAdminToken))
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctx)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("BLOCKED"));
    }

    @Test
    @DisplayName("should return SUSPICIOUS for round amount")
    void shouldReturnSuspiciousForRoundAmount() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000", new BigDecimal("5000.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
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
    @DisplayName("should return SUSPICIOUS for amount exceeding single limit")
    void shouldReturnSuspiciousForAmountExceedingSingleLimit() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000", new BigDecimal("9999999.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
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
    @DisplayName("should return SUSPICIOUS for after hours transaction")
    void shouldReturnSuspiciousForAfterHoursTransaction() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000", new BigDecimal("100.00"), "NGN",
                "4111111111111111", ip,
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
    @DisplayName("should return SUSPICIOUS for card velocity exceeded")
    void shouldReturnSuspiciousForCardVelocityExceeded() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000", new BigDecimal("100.00"), "NGN",
                "4111111111111112", ip,
                OffsetDateTime.now()
        );

        // 3 prior attempts — >= 4 means the 4th call trips the flag
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/fraud/evaluate")
                    .header("Authorization", bearerToken(superAdminToken))
                    .with(r -> { r.setRemoteAddr(ip); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ctx)));
        }

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
                "0000000000", new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
        );

        long before = fraudDao.countFraudAttempts();

        mockMvc.perform(post("/api/v1/fraud/evaluate")
                .header("Authorization", bearerToken(superAdminToken))
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ctx)));

        assertThat(fraudDao.countFraudAttempts()).isGreaterThan(before);
    }

    @Test
    @DisplayName("should require authentication on evaluate endpoint")
    void shouldRequireAuthOnEvaluate() throws Exception {
        String ip = uniqueIp();
        FraudEvaluationContext ctx = new FraudEvaluationContext(
                "0000000000", new BigDecimal("500.00"), "NGN",
                "4111111111111111", ip, OffsetDateTime.now()
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