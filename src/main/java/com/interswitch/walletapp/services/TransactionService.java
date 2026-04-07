package com.interswitch.walletapp.services;

import com.interswitch.walletapp.dao.TransactionDao;
import com.interswitch.walletapp.exceptions.ForbiddenException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.TransactionType;
import com.interswitch.walletapp.models.response.TransactionResponse;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionDao transactionDao;

    @Transactional
    public Long insertTransaction(Long accountId, Long transferId, TransactionType type,
                                  BigDecimal amount, String currency, Long createdBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("cardId", null)
                .addValue("transferId", transferId)
                .addValue("type", type.name())
                .addValue("amount", amount)
                .addValue("currency", currency)
                .addValue("createdBy", createdBy);

        return transactionDao.insert(params);
    }

    public TransactionResponse getTransactionById(Long transactionId) {
        return transactionDao.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    public Page<TransactionResponse> getTransactionsByAccount(Long accountId, int page, int size) {
        int offset = (page - 1) * size;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("limit", size)
                .addValue("offset", offset);

        long[] total = {0};
        List<TransactionResponse> transactions = transactionDao.findByAccountWithCount(params, total);

        return new PageImpl<>(transactions, PageRequest.of(page - 1, size), total[0]);
    }

    public Page<TransactionResponse> getTransactionsByAccountForSelf(Long accountId, int page, int size) {
        Long userId = SecurityUtil.getCurrentUserId();

        if (!transactionDao.isMerchantOwnerOfAccount(accountId, userId)) {
            throw new ForbiddenException("Account does not belong to your merchant");
        }

        return getTransactionsByAccount(accountId, page, size);
    }

    public BigDecimal getDailyDebitSum(Long accountId, OffsetDateTime from, OffsetDateTime to) {
        return transactionDao.getSumByTypeAndStatus(accountId, TransactionType.DEBIT.name(), "SUCCESS", from, to);
    }

    public BigDecimal getMonthlyDebitSum(Long accountId, OffsetDateTime from, OffsetDateTime to) {
        return transactionDao.getSumByTypeAndStatus(accountId, TransactionType.DEBIT.name(), "SUCCESS", from, to);
    }
}