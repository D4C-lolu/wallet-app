package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.MerchantBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantBlacklistRepository extends JpaRepository<MerchantBlacklist, Long> {

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
           "FROM MerchantBlacklist b " +
           "WHERE b.merchant.id = :merchantId AND b.liftedAt IS NULL")
    boolean isActivelyBlacklisted(@Param("merchantId") Long merchantId);
}
