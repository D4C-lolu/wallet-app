package com.interswitch.walletapp.services;

import com.interswitch.walletapp.dao.CardDao;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.ForbiddenException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.*;
import com.interswitch.walletapp.models.request.CreateCardRequest;
import com.interswitch.walletapp.models.request.CreateMyCardRequest;
import com.interswitch.walletapp.models.response.CardResponse;
import com.interswitch.walletapp.models.projections.CardValidationResult;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardDao cardDao;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "account_id", "card_number", "card_type",
            "scheme", "expiry_month", "expiry_year", "card_status",
            "created_at", "updated_at"
    );

    @Transactional
    public CardResponse createCardForSelf(CreateMyCardRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();

        cardDao.validateMerchantAccountOwnership(userId, request.accountId())
                .orElseThrow(() -> new ForbiddenException("Account does not belong to you"));

        return createCard(new CreateCardRequest(
                request.accountId(),
                request.cardNumber(),
                request.cardType(),
                request.scheme(),
                request.expiryMonth(),
                request.expiryYear()
        ));
    }

    @Transactional
    public CardResponse createCard(CreateCardRequest request) {
        validateCardExpiry(request.expiryMonth(), request.expiryYear());

        String cardHash = DigestUtils.sha256Hex(request.cardNumber());

        CardValidationResult validation = cardDao.getCardCreationValidation(request.accountId(), cardHash)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (validation.cardHashExists()) {
            throw new ConflictException("Card already exists");
        }

        if (validation.currentCardCount() >= validation.maxCards()) {
            throw new BadRequestException("Merchant has reached maximum number of cards for their tier");
        }

        String maskedCardNumber = maskCardNumber(request.cardNumber());
        Long createdBy = SecurityUtil.findCurrentUserId().orElse(null);

        Long id = cardDao.insert(request, cardHash, maskedCardNumber, createdBy);

        return new CardResponse(
                id,
                request.accountId(),
                maskedCardNumber,
                request.cardType(),
                request.scheme(),
                request.expiryMonth(),
                request.expiryYear(),
                CardStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    public CardResponse getCardById(Long cardId) {
        return cardDao.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Card not found"));
    }

    public void blockCard(Long cardId) {
        if (!cardDao.exists(cardId)) {
            throw new NotFoundException("Card not found");
        }

        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);
        cardDao.blockCard(cardId, updatedBy);
    }

    public void blockCardForSelf(Long cardId) {
        Long userId = SecurityUtil.getCurrentUserId();

        if (!cardDao.isMerchantOwnerOfCard(cardId, userId)) {
            throw new ForbiddenException("Card does not belong to your merchant");
        }

        blockCard(cardId);
    }

    public Page<CardResponse> getCardsByAccount(Long accountId, int page, int size, String sortField, Sort.Direction direction) {
        String safeSortField = validateSortField(sortField);
        int offset = (page - 1) * size;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("limit", size)
                .addValue("offset", offset);

        long[] total = {0};
        List<CardResponse> cards = cardDao.findByAccountWithCount(params, safeSortField, direction.name(), total);

        return new PageImpl<>(cards, PageRequest.of(page - 1, size), total[0]);
    }

    @Transactional
    public CardResponse updateCardStatus(Long cardId, CardStatus status) {
        CardResponse existing = getCardById(cardId);
        Long updatedBy = SecurityUtil.findCurrentUserId().orElse(null);

        cardDao.updateStatus(cardId, status.name(), updatedBy);

        return new CardResponse(
                existing.id(),
                existing.accountId(),
                existing.cardNumber(),
                existing.cardType(),
                existing.scheme(),
                existing.expiryMonth(),
                existing.expiryYear(),
                status,
                existing.createdAt(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public void deleteCard(Long cardId) {
        if (!cardDao.exists(cardId)) {
            throw new NotFoundException("Card not found");
        }

        Long deletedBy = SecurityUtil.getCurrentUserId();
        cardDao.softDelete(cardId, deletedBy);
    }

    private void validateCardExpiry(int expiryMonth, int expiryYear) {
        YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
        if (expiry.isBefore(YearMonth.now())) {
            throw new BadRequestException("Card expiry date is in the past");
        }
    }

    private String maskCardNumber(String cardNumber) {
        return cardNumber.substring(0, 4) +
                "*".repeat(cardNumber.length() - 8) +
                cardNumber.substring(cardNumber.length() - 4);
    }

    private String validateSortField(String sortField) {
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException("Invalid sort field: " + sortField);
        }
        return sortField;
    }
}