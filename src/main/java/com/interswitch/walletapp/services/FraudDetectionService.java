package com.interswitch.walletapp.services;

import com.interswitch.walletapp.context.BlacklistCache;
import com.interswitch.walletapp.dao.FraudDao;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import com.interswitch.walletapp.models.response.FraudAttemptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudDao fraudDao;
    private final FraudAttemptLoggerService fraudLogger;
    private final FraudConsequenceService fraudConsequenceService;
    private final RateLimiterService rateLimiter;
    private final BlacklistCache blacklistCache;

    //TODO: make configurable
    private static final int CARD_VELOCITY_THRESHOLD = 3;
    private static final int CARD_VELOCITY_WINDOW_SECONDS = 60;
    private static final int BUSINESS_HOURS_START = 6;
    private static final int BUSINESS_HOURS_END = 22;

    public FraudStatus evaluate(FraudEvaluationContext ctx) {
        String cardHash = DigestUtils.sha256Hex(ctx.cardNumber());
        List<String> flags = new ArrayList<>();
        FraudStatus status = FraudStatus.CLEAN;

        Long merchantId = fraudDao.getMerchantIdByAccount(ctx.accountNumber()).orElse(null);

        // Hard blocks
        if (merchantId != null && blacklistCache.isBlacklisted(merchantId)) {
            flags.add("MERCHANT_BLACKLISTED");
            fraudLogger.logAttempt(cardHash, ctx, merchantId, FraudStatus.BLOCKED, flags);
            applyConsequences(ctx, cardHash, FraudStatus.BLOCKED, flags);
            return FraudStatus.BLOCKED;
        }

        if (rateLimiter.isRateLimited(ctx.ipAddress())) {
            flags.add("RATE_LIMITED");
            fraudLogger.logAttempt(cardHash, ctx, merchantId, FraudStatus.BLOCKED, flags);
            applyConsequences(ctx, cardHash, FraudStatus.BLOCKED, flags);
            return FraudStatus.BLOCKED;
        }

        // Soft flags
        if (isCardVelocityExceeded(cardHash)) {
            flags.add("CARD_VELOCITY_EXCEEDED");
            status = FraudStatus.SUSPICIOUS;
        }

        if (isAmountExceedsSingleLimitByAccount(ctx.accountNumber(), ctx.amount())) {
            flags.add("EXCEEDS_SINGLE_LIMIT");
            status = FraudStatus.SUSPICIOUS;
        }

        if (isAfterHours(ctx.transactionTime())) {
            flags.add("AFTER_HOURS");
            status = FraudStatus.SUSPICIOUS;
        }

        fraudLogger.logAttempt(cardHash, ctx, merchantId, status, flags);

        // Apply consequences for non-clean statuses
        if (status != FraudStatus.CLEAN) {
            applyConsequences(ctx, cardHash, status, flags);
        }

        return status;
    }

    private void applyConsequences(FraudEvaluationContext ctx, String cardHash, FraudStatus status, List<String> flags) {
        try {
            fraudConsequenceService.applyConsequences(ctx, cardHash, status, flags);
        } catch (Exception e) {
            log.error("Failed to apply fraud consequences for account: {}", ctx.accountNumber(), e);
        }
    }

    public Page<FraudAttemptResponse> getFraudAttempts(int page, int size) {
        int offset = (page - 1) * size;
        List<FraudAttemptResponse> attempts = fraudDao.getFraudAttempts(size, offset);
        long total = fraudDao.countFraudAttempts();
        return new PageImpl<>(attempts, PageRequest.of(page - 1, size), total);
    }

    private boolean isCardVelocityExceeded(String cardHash) {
        OffsetDateTime since = OffsetDateTime.now().minusSeconds(CARD_VELOCITY_WINDOW_SECONDS);
        return fraudDao.getCardVelocityCount(cardHash, since) >= CARD_VELOCITY_THRESHOLD;
    }

    private boolean isAmountExceedsSingleLimit(Long merchantId, BigDecimal amount) {
        return fraudDao.getMerchantSingleLimit(merchantId)
                .map(limit -> amount.compareTo(limit) > 0)
                .orElse(false);
    }

    private boolean isAmountExceedsSingleLimitByAccount(String accountNumber, BigDecimal amount) {
        return fraudDao.getMerchantSingleLimitByAccount(accountNumber)
                .map(limit -> amount.compareTo(limit) > 0)
                .orElse(false);
    }

    private boolean isAfterHours(OffsetDateTime time) {
        int hour = time.getHour();
        return hour < BUSINESS_HOURS_START || hour >= BUSINESS_HOURS_END;
    }
}