package com.interswitch.walletapp.controllers.v1;

import com.interswitch.walletapp.base.BaseControllerIntegrationTest;

import com.interswitch.walletapp.entities.TierConfig;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.request.UpdateTierConfigRequest;
import com.interswitch.walletapp.repositories.TierConfigRepository;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@DisplayName("Tier Config Controller Integration Tests")
public class TierConfigControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private TierConfigRepository tierConfigRepository;

    @Test
    @DisplayName("should get all tier configs successfully")
    void shouldGetAllTierConfigsSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");

        mockMvc.perform(get("/api/v1/tier-configs")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("should get tier config by tier successfully")
    void shouldGetTierConfigByTierSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        TierConfig existing = tierConfigRepository.findByTier(MerchantTier.TIER_1).orElseThrow();

        mockMvc.perform(get("/api/v1/tier-configs/tier/{tier}", MerchantTier.TIER_1)
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(existing.getId()))
                .andExpect(jsonPath("$.data.tier").value(MerchantTier.TIER_1.name()));
    }

    @Test
    @DisplayName("should update tier config successfully as super admin")
    void shouldUpdateTierConfigSuccessfully() throws Exception {
        String token = loginAndGetAccessToken("superadmin@verveguard.com", "Admin123!");
        UpdateTierConfigRequest request = new UpdateTierConfigRequest(
                new BigDecimal("200000.0000"),
                new BigDecimal("20000.0000"),
                new BigDecimal("2000000.0000"),
                3, 2
        );

        mockMvc.perform(put("/api/v1/tier-configs/{tier}", MerchantTier.TIER_1)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyTransactionLimit").value(request.dailyTransactionLimit().doubleValue()))
                .andExpect(jsonPath("$.data.maxCards").value(request.maxCards()));
    }

    @Test
    @DisplayName("should return 403 when merchant tries to update tier config")
    void shouldReturn403WhenMerchantTriesToUpdateTierConfig() throws Exception {
        String token = loginAndGetAccessToken("testmerchant@verveguard.com", "Admin123!");
        UpdateTierConfigRequest request = new UpdateTierConfigRequest(
                new BigDecimal("200000.0000"),
                new BigDecimal("20000.0000"),
                new BigDecimal("2000000.0000"),
                3, 2
        );

        mockMvc.perform(put("/api/v1/tier-configs/{tier}", MerchantTier.TIER_1)
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 401 when unauthenticated")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/tier-configs"))
                .andExpect(status().isUnauthorized());
    }
}