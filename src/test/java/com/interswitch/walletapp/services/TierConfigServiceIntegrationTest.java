package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.TierConfig;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.request.UpdateTierConfigRequest;
import com.interswitch.walletapp.models.response.TierConfigResponse;
import com.interswitch.walletapp.repositories.TierConfigRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tier Config Service Integration Tests")
public class TierConfigServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TierConfigService tierConfigService;

    @Autowired
    private TierConfigRepository tierConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setupSecurityContext() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should get tier config by tier successfully")
    void shouldGetTierConfigByTierSuccessfully() {
        TierConfig existing = tierConfigRepository.findByTier(MerchantTier.TIER_1).orElseThrow();

        TierConfigResponse response = tierConfigService.getTierConfigByTier(MerchantTier.TIER_1);

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.tier()).isEqualTo(existing.getTier());
        assertThat(response.dailyTransactionLimit()).isEqualTo(existing.getDailyTransactionLimit());
        assertThat(response.singleTransactionLimit()).isEqualTo(existing.getSingleTransactionLimit());
        assertThat(response.monthlyTransactionLimit()).isEqualTo(existing.getMonthlyTransactionLimit());
        assertThat(response.maxCards()).isEqualTo(existing.getMaxCards());
        assertThat(response.maxAccounts()).isEqualTo(existing.getMaxAccounts());
    }

    @Test
    @DisplayName("should get all tier configs successfully")
    void shouldGetAllTierConfigsSuccessfully() {
        List<TierConfigResponse> configs = tierConfigService.getAllTierConfigs();

        assertEquals(3, configs.size());
        assertThat(configs.stream().map(TierConfigResponse::tier).toList())
                .containsExactlyInAnyOrder(MerchantTier.values());
    }

    @Test
    @DisplayName("should update tier config successfully")
    void shouldUpdateTierConfigSuccessfully() {
        tierConfigRepository.findByTier(MerchantTier.TIER_1).orElseThrow();
        UpdateTierConfigRequest request = new UpdateTierConfigRequest(
                new BigDecimal("200000.0000"),
                new BigDecimal("20000.0000"),
                new BigDecimal("2000000.0000"),
                3, 2
        );

        TierConfigResponse response = tierConfigService.updateTierConfig(MerchantTier.TIER_1, request);

        assertThat(response.dailyTransactionLimit()).isEqualTo(request.dailyTransactionLimit());
        assertThat(response.singleTransactionLimit()).isEqualTo(request.singleTransactionLimit());
        assertThat(response.monthlyTransactionLimit()).isEqualTo(request.monthlyTransactionLimit());
        assertThat(response.maxCards()).isEqualTo(request.maxCards());
        assertThat(response.maxAccounts()).isEqualTo(request.maxAccounts());
    }

    @Test
    @DisplayName("should fail get tier config with non existent tier")
    void shouldFailGetTierConfigWithNonExistentTier() {
        tierConfigRepository.findByTier(MerchantTier.TIER_1).ifPresent(tierConfigRepository::delete);

        assertThatThrownBy(() -> tierConfigService.getTierConfigByTier(MerchantTier.TIER_1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Tier config not found for TIER_1");
    }
}