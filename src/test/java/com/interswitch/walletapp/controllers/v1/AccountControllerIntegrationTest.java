package com.interswitch.walletapp.controllers.v1;


import com.interswitch.walletapp.base.BaseControllerIntegrationTest;

import com.interswitch.walletapp.models.enums.AccountStatus;
import com.interswitch.walletapp.models.enums.AccountType;
import com.interswitch.walletapp.models.request.CreateAccountRequest;
import com.interswitch.walletapp.models.request.CreateMyAccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Account Controller Integration Tests")
public class AccountControllerIntegrationTest extends BaseControllerIntegrationTest {

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        merchantToken = loginAndGetAccessToken("testmerchant3@verveguard.com", "Admin123!");
    }

    @Test
    @DisplayName("should create account successfully as super admin")
    void shouldCreateAccountSuccessfully() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(3L, AccountType.WALLET, "NGN");

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.merchantId").value(request.merchantId()))
                .andExpect(jsonPath("$.data.accountType").value(request.accountType().name()))
                .andExpect(jsonPath("$.data.currency").value(request.currency()))
                .andExpect(jsonPath("$.data.accountStatus").value(AccountStatus.ACTIVE.name()));
    }

    @Test
    @DisplayName("should return 403 when merchant tries to create account via admin endpoint")
    void shouldReturn403WhenMerchantTriesToCreateAccountViaAdminEndpoint() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(1L, AccountType.WALLET, "NGN");

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearerToken(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should create account for self successfully as merchant")
    void shouldCreateAccountForSelfSuccessfully() throws Exception {
        CreateMyAccountRequest request = new CreateMyAccountRequest(AccountType.WALLET, "NGN");

        merchantToken = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");

        mockMvc.perform(post("/api/v1/accounts/me")
                        .header("Authorization", bearerToken(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountType").value(request.accountType().name()))
                .andExpect(jsonPath("$.data.currency").value(request.currency()))
                .andExpect(jsonPath("$.data.accountStatus").value(AccountStatus.ACTIVE.name()));
    }

    @Test
    @DisplayName("should return 403 when admin tries to create account via merchant endpoint")
    void shouldReturn403WhenAdminTriesToCreateAccountViaMerchantEndpoint() throws Exception {
        CreateMyAccountRequest request = new CreateMyAccountRequest(AccountType.WALLET, "NGN");

        mockMvc.perform(post("/api/v1/accounts/me")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should get account by id successfully")
    void shouldGetAccountByIdSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}", 1L)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.merchantId").isNotEmpty());
    }

    @Test
    @DisplayName("should return 404 for non existent account")
    void shouldReturn404ForNonExistentAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should get accounts by merchant successfully")
    void shouldGetAccountsByMerchantSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/merchant/{merchantId}", 1L)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("should update account status successfully")
    void shouldUpdateAccountStatusSuccessfully() throws Exception {
        mockMvc.perform(patch("/api/v1/accounts/{accountId}/status", 1L)
                        .header("Authorization", bearerToken(superAdminToken))
                        .param("status", AccountStatus.SUSPENDED.name()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{accountId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("should delete account successfully")
    void shouldDeleteAccountSuccessfully() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(2L, AccountType.ESCROW, "NGN");

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long accountId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(delete("/api/v1/accounts/{accountId}", accountId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNoContent());
    }
}