package com.interswitch.walletapp.services;


import com.interswitch.walletapp.dao.AccountDao;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.AccountStatus;
import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.request.AccountResponse;
import com.interswitch.walletapp.models.request.CreateAccountRequest;
import com.interswitch.walletapp.models.request.CreateMyAccountRequest;
import com.interswitch.walletapp.models.projections.MerchantAccountValidation;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountDao accountDao;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "merchant_id", "account_number", "account_type",
            "currency", "balance", "ledger_balance", "account_status",
            "created_at", "updated_at"
    );

    @Transactional
    public AccountResponse createAccountForSelf(CreateMyAccountRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();

        Long merchantId = accountDao.findMerchantIdByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Merchant account not found for current user"));

        return createAccount(new CreateAccountRequest(merchantId, request.accountType(), request.currency()));
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        MerchantAccountValidation validation = accountDao.getMerchantAccountValidation(request.merchantId())
                .orElseThrow(() -> new NotFoundException("Merchant not found"));

        if (!KycStatus.APPROVED.name().equals(validation.kycStatus())) {
            throw new BadRequestException("Merchant must be KYC approved to create an account");
        }

        if (validation.currentAccountCount() >= validation.maxAccounts()) {
            throw new BadRequestException("Merchant has reached maximum number of accounts for their tier");
        }

        String accountNumber = accountDao.nextAccountNumber();
        Long createdBy = SecurityUtil.findCurrentUserId().orElse(null);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("merchantId", request.merchantId())
                .addValue("accountNumber", accountNumber)
                .addValue("accountType", request.accountType().name())
                .addValue("currency", request.currency())
                .addValue("accountStatus", AccountStatus.ACTIVE.name())
                .addValue("createdBy", createdBy);

        Long id = accountDao.insert(params);

        return new AccountResponse(
                id,
                request.merchantId(),
                accountNumber,
                request.accountType(),
                request.currency(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                AccountStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    public AccountResponse getAccountById(Long accountId) {
        return accountDao.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    public Page<AccountResponse> getAccountsByMerchant(Long merchantId, int page, int size, String sortField, Sort.Direction direction) {
        String safeSortField = validateSortField(sortField);
        int offset = (page - 1) * size;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("limit", size)
                .addValue("offset", offset);

        long[] total = {0};
        List<AccountResponse> accounts = accountDao.findByMerchantWithCount(params, safeSortField, direction.name(), total);

        return new PageImpl<>(accounts, PageRequest.of(page - 1, size), total[0]);
    }

    @Transactional
    public AccountResponse updateAccountStatus(Long accountId, AccountStatus status) {
        AccountResponse existing = getAccountById(accountId);

        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);
        accountDao.updateStatus(new MapSqlParameterSource()
                .addValue("id", accountId)
                .addValue("status", status.name())
                .addValue("updatedBy", updatedBy));

        return new AccountResponse(
                existing.id(),
                existing.merchantId(),
                existing.accountNumber(),
                existing.accountType(),
                existing.currency(),
                existing.balance(),
                existing.ledgerBalance(),
                status,
                existing.createdAt(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public void deleteAccount(Long accountId) {
        if (!accountDao.exists(accountId)) {
            throw new NotFoundException("Account not found");
        }

        Long deletedBy = SecurityUtil.getCurrentUserId();
        accountDao.softDelete(new MapSqlParameterSource()
                .addValue("id", accountId)
                .addValue("deletedBy", deletedBy));
    }

    private String validateSortField(String sortField) {
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException("Invalid sort field: " + sortField);
        }
        return sortField;
    }
}