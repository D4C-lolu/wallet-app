package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.Account;
import com.interswitch.walletapp.models.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findAllByMerchantId(Long merchant_id);

    List<Account> findAllByMerchantIdAndDeletedAtIsNull(Long merchant_id);

    List<Account> findAllByAccountStatus(AccountStatus accountStatus);

    boolean existsByAccountNumber(String accountNumber);

    int countByMerchantIdAndDeletedAtIsNull(String merchantId);
}
