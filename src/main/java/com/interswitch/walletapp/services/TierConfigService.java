package com.interswitch.walletapp.services;

import com.interswitch.walletapp.entities.TierConfig;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.mapper.TierConfigMapper;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.request.CreateTierConfigRequest;
import com.interswitch.walletapp.models.request.UpdateTierConfigRequest;
import com.interswitch.walletapp.models.response.TierConfigResponse;
import com.interswitch.walletapp.repositories.TierConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TierConfigService {

    private final TierConfigRepository tierConfigRepository;
    private final TierConfigMapper tierConfigMapper;

    @Transactional
    public TierConfigResponse createTierConfig(CreateTierConfigRequest request) {
        if (tierConfigRepository.existsByTier(request.tier())) {
            throw new ConflictException("Tier config already exists for " + request.tier());
        }

        TierConfig tierConfig = TierConfig.builder()
                .tier(request.tier())
                .dailyTransactionLimit(request.dailyTransactionLimit())
                .singleTransactionLimit(request.singleTransactionLimit())
                .monthlyTransactionLimit(request.monthlyTransactionLimit())
                .maxCards(request.maxCards())
                .maxAccounts(request.maxAccounts())
                .build();

        return tierConfigMapper.map(tierConfigRepository.save(tierConfig));
    }

    @Transactional
    public TierConfigResponse updateTierConfig(MerchantTier tier, UpdateTierConfigRequest request) {
        TierConfig tierConfig = tierConfigRepository.findByTier(tier)
                .orElseThrow(() -> new NotFoundException("Tier config not found"));

        tierConfig.setDailyTransactionLimit(request.dailyTransactionLimit());
        tierConfig.setSingleTransactionLimit(request.singleTransactionLimit());
        tierConfig.setMonthlyTransactionLimit(request.monthlyTransactionLimit());
        tierConfig.setMaxCards(request.maxCards());
        tierConfig.setMaxAccounts(request.maxAccounts());

        return tierConfigMapper.map(tierConfigRepository.save(tierConfig));
    }

    public TierConfigResponse getTierConfigById(Long tierConfigId) {
        return tierConfigMapper.map(tierConfigRepository.findById(tierConfigId)
                .orElseThrow(() -> new NotFoundException("Tier config not found")));
    }

    public TierConfigResponse getTierConfigByTier(MerchantTier tier) {
        return tierConfigMapper.map(tierConfigRepository.findByTier(tier)
                .orElseThrow(() -> new NotFoundException("Tier config not found for " + tier)));
    }

    public List<TierConfigResponse> getAllTierConfigs() {
        return tierConfigMapper.map(tierConfigRepository.findAll());
    }
}