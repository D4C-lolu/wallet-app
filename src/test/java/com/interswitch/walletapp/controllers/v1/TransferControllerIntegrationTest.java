package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;

import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.enums.TransferStatus;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.repositories.AccountRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import com.interswitch.walletapp.services.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Transfer Controller Integration Tests")
public class TransferControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    private Long fromAccountId;
    private Long toAccountId;

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        merchantToken   = loginAndGetAccessToken("demo.merchant@verveguard.com", "Admin123!");

        fromAccountId = accountRepository.findByAccountNumber("0000000000").orElseThrow().getId();
        toAccountId   = accountRepository.findByAccountNumber("1100000001").orElseThrow().getId();
    }

    private TransferRequest buildRequest(String reference) {
        return new TransferRequest(
                reference, fromAccountId, toAccountId,
                new BigDecimal("1000.00"), "NGN", "Test transfer", "4111111111111111"
        );
    }

    @Test
    @DisplayName("should transfer for self successfully as merchant")
    void shouldTransferForSelfSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/transfers/me")
                        .header("Authorization", bearerToken(merchantToken))
                        .with(r -> { r.setRemoteAddr(uniqueIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("REF-CTRL-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference").value("REF-CTRL-001"))
                .andExpect(jsonPath("$.data.transferStatus").value(TransferStatus.SUCCESS.name()));
    }

    @Test
    @DisplayName("should return 409 with duplicate reference")
    void shouldReturn409WithDuplicateReference() throws Exception {
        TransferRequest request = buildRequest("REF-CTRL-002");
        mockMvc.perform(post("/api/v1/transfers/me")
                .header("Authorization", bearerToken(merchantToken))
                .with(r -> { r.setRemoteAddr(uniqueIp()); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/v1/transfers/me")
                        .header("Authorization", bearerToken(merchantToken))
                        .with(r -> { r.setRemoteAddr(uniqueIp()); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}

