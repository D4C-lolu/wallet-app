package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;

import com.interswitch.walletapp.models.enums.*;
import com.interswitch.walletapp.models.request.CreateCardRequest;
import com.interswitch.walletapp.models.request.CreateMyCardRequest;
import com.interswitch.walletapp.repositories.AccountRepository;
import com.interswitch.walletapp.repositories.CardRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Card Controller Integration Tests")
public class CardControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    private Long demoAccountId;
    private Long demoCardId;

    @BeforeEach
    void setup() throws Exception {
        superAdminToken = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        merchantToken = loginAndGetAccessToken("demo.merchant@verveguard.com", "Admin123!");

        demoAccountId = accountRepository.findByAccountNumber("0000000000")
                .orElseThrow().getId();

        demoCardId = cardRepository.findByCardHash(DigestUtils.sha256Hex("4011111111111111"))
                .orElseThrow().getId();
    }

    private Long createTestCard(String cardNumber) throws Exception {
        CreateCardRequest request = new CreateCardRequest(
                demoAccountId,
                cardNumber,
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );
        MvcResult result = mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    @Test
    @DisplayName("should create card successfully as super admin")
    void shouldCreateCardSuccessfully() throws Exception {
        CreateCardRequest request = new CreateCardRequest(
                demoAccountId,
                "4111222233334444",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(demoAccountId))
                .andExpect(jsonPath("$.data.cardType").value(request.cardType().name()))
                .andExpect(jsonPath("$.data.scheme").value(request.scheme().name()))
                .andExpect(jsonPath("$.data.cardStatus").value(CardStatus.ACTIVE.name()));
    }

    @Test
    @DisplayName("should return 403 when merchant tries to create card via admin endpoint")
    void shouldReturn403WhenMerchantTriesToCreateCardViaAdminEndpoint() throws Exception {
        CreateCardRequest request = new CreateCardRequest(
                demoAccountId,
                "4111222233335555",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", bearerToken(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should create card for self successfully as merchant")
    void shouldCreateCardForSelfSuccessfully() throws Exception {
        CreateMyCardRequest request = new CreateMyCardRequest(
                demoAccountId,
                "4111222233336666",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        mockMvc.perform(post("/api/v1/cards/me")
                        .header("Authorization", bearerToken(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value(demoAccountId))
                .andExpect(jsonPath("$.data.cardStatus").value(CardStatus.ACTIVE.name()));
    }

    @Test
    @DisplayName("should return 403 when admin tries to create card via merchant endpoint")
    void shouldReturn403WhenAdminTriesToCreateCardViaMerchantEndpoint() throws Exception {
        CreateMyCardRequest request = new CreateMyCardRequest(
                demoAccountId,
                "4111222233337777",
                CardType.VIRTUAL,
                CardScheme.VISA,
                12, 3028
        );

        mockMvc.perform(post("/api/v1/cards/me")
                        .header("Authorization", bearerToken(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should get card by id successfully")
    void shouldGetCardByIdSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardId}", demoCardId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(demoCardId))
                .andExpect(jsonPath("$.data.accountId").value(demoAccountId));
    }

    @Test
    @DisplayName("should return 404 for non existent card")
    void shouldReturn404ForNonExistentCard() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardId}", Long.MAX_VALUE)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should get cards by account successfully")
    void shouldGetCardsByAccountSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/cards/account/{accountId}", demoAccountId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("should update card status successfully")
    void shouldUpdateCardStatusSuccessfully() throws Exception {
        Long createdId = createTestCard("4111222233338888");

        mockMvc.perform(patch("/api/v1/cards/{cardId}/status", createdId)
                        .header("Authorization", bearerToken(superAdminToken))
                        .param("status", CardStatus.BLOCKED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardStatus").value(CardStatus.BLOCKED.name()));
    }

    @Test
    @DisplayName("should block card successfully as admin")
    void shouldBlockCardSuccessfullyAsAdmin() throws Exception {
        Long createdId = createTestCard("4111222233339999");

        mockMvc.perform(patch("/api/v1/cards/{cardId}/block", createdId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should block card for self successfully as merchant")
    void shouldBlockCardForSelfSuccessfully() throws Exception {
        mockMvc.perform(patch("/api/v1/cards/{cardId}/block/me", demoCardId)
                        .header("Authorization", bearerToken(merchantToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 403 when merchant tries to block card they dont own")
    void shouldReturn403WhenMerchantTriesToBlockCardTheyDontOwn() throws Exception {
        String otherMerchantToken = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");

        mockMvc.perform(patch("/api/v1/cards/{cardId}/block/me", demoCardId)
                        .header("Authorization", bearerToken(otherMerchantToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should delete card successfully")
    void shouldDeleteCardSuccessfully() throws Exception {
        Long createdId = createTestCard("4111222200001111");

        mockMvc.perform(delete("/api/v1/cards/{cardId}", createdId)
                        .header("Authorization", bearerToken(superAdminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardId}", demoCardId))
                .andExpect(status().isUnauthorized());
    }
}