package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.annotation.DisableFraudDetection;
import com.interswitch.walletapp.base.BaseControllerIntegrationTest;

import com.interswitch.walletapp.models.enums.TransferStatus;
import com.interswitch.walletapp.models.request.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisableFraudDetection
@DisplayName("Transfer Controller Integration Tests")
public class TransferControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final String FROM_ACCOUNT_NUMBER = "0000000000";
    private static final String TO_ACCOUNT_NUMBER = "1100000001";

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        merchantToken   = loginAndGetAccessToken("demo.merchant@verveguard.com", "Admin123!");
    }

    private TransferRequest buildRequest() {
        return new TransferRequest(FROM_ACCOUNT_NUMBER, TO_ACCOUNT_NUMBER,
                new BigDecimal("1000.00"), "NGN", "Test transfer", "4111111111111111"
        );
    }

    @Test
    @DisplayName("should transfer for self successfully as merchant")
    void shouldTransferForSelfSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/me")
                        .header("Authorization", bearerToken(merchantToken))
                        .header("X-Idempotency-Key", "REF-CTRL-001")
                        .with(r -> { r.setRemoteAddr(uniqueIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference").value("REF-CTRL-001"))
                .andExpect(jsonPath("$.data.transferStatus").value(TransferStatus.SUCCESS.name()));
    }

    @Test
    @DisplayName("should return same response for idempotent duplicate")
    void shouldReturnSameResponseForIdempotentDuplicate() throws Exception {
        TransferRequest request = buildRequest();
        String reference = "REF-CTRL-002";
        mockMvc.perform(post("/api/v1/transfers/me")
                .header("Authorization", bearerToken(merchantToken))
                        .header("X-Idempotency-Key", reference)
                        .header("Content-Type", MediaType.APPLICATION_JSON)
                .with(r -> { r.setRemoteAddr(uniqueIp()); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/v1/transfers/me")
                        .header("Authorization", bearerToken(merchantToken))
                        .header("X-Idempotency-Key", reference)
                        .with(r -> { r.setRemoteAddr(uniqueIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}

