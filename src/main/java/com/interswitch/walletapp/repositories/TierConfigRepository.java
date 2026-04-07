package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.TierConfig;
import com.interswitch.walletapp.models.enums.MerchantTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TierConfigRepository extends JpaRepository<TierConfig, Long> {

    Optional<TierConfig> findByTier(MerchantTier tier);

    boolean existsByTier(MerchantTier tier);
}
