package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.Merchant;
import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.projections.MerchantProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByUserId(Long user_id);

    List<Merchant> findAllByDeletedAtIsNull();

    Optional<Merchant> findByUserEmail(String email);

    List<Merchant> findAllByMerchantStatus(MerchantStatus merchantStatus);

    List<Merchant> findAllByKycStatus(KycStatus kycStatus);

    boolean existsByUserId(Long user_id);

    @Query("SELECT m.id as id, m.address as address, m.kycStatus as kycStatus, " +
            "m.merchantStatus as merchantStatus, m.tier as tier, " +
            "m.createdAt as createdAt, m.updatedAt as updatedAt, " +
            "u.id as userId, u.firstname as userFirstname, u.lastname as userLastname, " +
            "u.email as userEmail, u.phone as userPhone, u.userStatus as userStatus " +
            "FROM Merchant m JOIN m.user u WHERE m.deletedAt IS NULL")
    Page<MerchantProjection> findAllMerchants(Pageable pageable);

    @Query("SELECT m.id as id, m.address as address, m.kycStatus as kycStatus, " +
            "m.merchantStatus as merchantStatus, m.tier as tier, " +
            "m.createdAt as createdAt, m.updatedAt as updatedAt, " +
            "u.id as userId, u.firstname as userFirstname, u.lastname as userLastname, " +
            "u.email as userEmail, u.phone as userPhone, u.userStatus as userStatus " +
            "FROM Merchant m JOIN m.user u WHERE m.id = :merchantId AND m.deletedAt IS NULL")
    Optional<MerchantProjection> findMerchantById(@Param("merchantId") Long merchantId);

}
