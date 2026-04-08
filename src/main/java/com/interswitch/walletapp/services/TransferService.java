package com.interswitch.walletapp.services;

import com.interswitch.walletapp.annotation.FraudChecked;
import com.interswitch.walletapp.annotation.Idempotent;
import com.interswitch.walletapp.dao.TransferDao;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.ForbiddenException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.AccountStatus;
import com.interswitch.walletapp.models.enums.TransactionType;
import com.interswitch.walletapp.models.enums.TransferStatus;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.models.response.TransferResponse;
import com.interswitch.walletapp.models.projections.TransferValidationResult;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferDao transferDao;
    private final TransactionService transactionService;


    private TransferResponse transfer(TransferRequest request, String reference) {
        Long createdBy = SecurityUtil.findCurrentUserId().orElse(null);

        TransferValidationResult validationResult = transferDao.getTransferValidation(request.fromAccountNumber(), request.toAccountNumber(), reference)
                .orElseThrow(() -> new NotFoundException("One or both accounts not found"));

        Long fromAccountId = validationResult.fromAccountId();
        Long toAccountId = validationResult.toAccountId();

        // Validation checks
        if (validationResult.referenceExists() > 0) {
            throw new ConflictException("Transfer reference already exists");
        }
        if (!request.currency().equals(validationResult.fromCurrency()) || !request.currency().equals(validationResult.toCurrency())) {
            throw new BadRequestException("Account currency does not match transfer currency");
        }
        if (!AccountStatus.ACTIVE.name().equals(validationResult.fromStatus()) || !AccountStatus.ACTIVE.name().equals(validationResult.toStatus())) {
            throw new BadRequestException("One or both accounts are not active");
        }
        if (validationResult.fromBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Insufficient funds");
        }

        // Limit Check
        validateLimits(request, validationResult, fromAccountId);

        // Execution
        Long transferId = transferDao.insert(fromAccountId, toAccountId, request, reference, createdBy);

        // Log transaction legs
        transactionService.insertTransaction(fromAccountId, transferId, TransactionType.DEBIT, request.amount(), request.currency(), createdBy);
        transactionService.insertTransaction(toAccountId, transferId, TransactionType.CREDIT, request.amount(), request.currency(), createdBy);

        // Update balances
        transferDao.debitAccount(fromAccountId, request.amount());
        transferDao.creditAccount(toAccountId, request.amount());

        // Finalize status
        transferDao.updateStatus(transferId, TransferStatus.SUCCESS.name());

        return getTransferById(transferId);
    }

    private void validateLimits(TransferRequest request, TransferValidationResult validationResult, Long fromAccountId) {
        if (request.amount().compareTo(validationResult.singleLimit()) > 0) {
            throw new BadRequestException("Amount exceeds single transaction limit for your tier");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay().atOffset(now.getOffset());

        BigDecimal dailyTotal = transactionService.getDailyDebitSum(fromAccountId, startOfDay, now);
        if (dailyTotal.add(request.amount()).compareTo(validationResult.dailyLimit()) > 0) {
            throw new BadRequestException("Amount exceeds daily transaction limit for your tier");
        }

        BigDecimal monthlyTotal = transactionService.getMonthlyDebitSum(fromAccountId, startOfMonth, now);
        if (monthlyTotal.add(request.amount()).compareTo(validationResult.monthlyLimit()) > 0) {
            throw new BadRequestException("Amount exceeds monthly transaction limit for your tier");
        }
    }

    @FraudChecked
    @Idempotent
    @Transactional
    public TransferResponse transferForSelf(TransferRequest request, String reference) {
        if (!transferDao.isMerchantOwnerOfAccountByNumber(request.fromAccountNumber(), SecurityUtil.getCurrentUserId())) {
            throw new ForbiddenException("Account does not belong to your merchant");
        }

        try {
            return transfer(request, reference);
        } catch (DataIntegrityViolationException e) {
            log.error("Duplicate transaction detected for reference: {}", reference);
            throw new ConflictException("This transfer is already being processed.");
        }
    }

    public TransferResponse getTransferById(Long transferId) {
        return transferDao.findById(transferId)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
    }

    public Page<TransferResponse> getTransfersByAccount(Long accountId, int page, int size) {
        int offset = (page - 1) * size;
        long[] total = {0};
        List<TransferResponse> transfers = transferDao.findByAccountWithPage(accountId, size, offset, total);
        return new PageImpl<>(transfers, PageRequest.of(page - 1, size), total[0]);
    }

    public Page<TransferResponse> getTransfersByAccountForSelf(Long accountId, int page, int size) {
        if (!transferDao.isMerchantOwnerOfAccount(accountId, SecurityUtil.getCurrentUserId())) {
            throw new ForbiddenException("Account does not belong to your merchant");
        }
        return getTransfersByAccount(accountId, page, size);
    }
}