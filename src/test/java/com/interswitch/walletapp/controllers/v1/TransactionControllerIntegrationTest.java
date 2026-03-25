package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.repositories.AccountRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import com.interswitch.walletapp.services.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Transaction Controller Integration Tests")
public class TransactionControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    private Long fromAccountId;
    private Long toAccountId;

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        merchantToken   = loginAndGetAccessToken("demo.merchant@verveguard.com", "Admin123!");

        fromAccountId = accountRepository.findByAccountNumber("0000000000").orElseThrow().getId();
        toAccountId   = accountRepository.findByAccountNumber("1100000001").orElseThrow().getId();

        User merchant = userRepository.findByEmail("demo.merchant@verveguard.com").orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(merchant), null, List.of())
        );
        transferService.transferForSelf(new TransferRequest(
                "REF-TXN-SETUP", fromAccountId, toAccountId,
                new BigDecimal("1000.00"), "NGN", "Setup transfer", "4111111111111111"
        ));
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should get transactions by account successfully as admin")
    void shouldGetTransactionsByAccountSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/account/{accountId}", fromAccountId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("should get transaction by id successfully as admin")
    void shouldGetTransactionByIdSuccessfully() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/v1/transactions/account/{accountId}", fromAccountId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andReturn();

        Long transactionId = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .get("data")
                .get("content").get(0).get("id").asLong();

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(transactionId))
                .andExpect(jsonPath("$.data.accountId").value(fromAccountId));
    }
}
