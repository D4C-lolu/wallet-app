package com.interswitch.walletapp.services;

import com.interswitch.verveguard.api.FraudDecision;
import com.interswitch.verveguard.api.FraudEvaluator;
import com.interswitch.verveguard.api.model.FraudContext;
import com.interswitch.verveguard.api.model.FraudResult;
import com.interswitch.verveguard.api.model.GateResult;
import com.interswitch.walletapp.dao.FraudDao;
import com.interswitch.walletapp.fraud.FraudDataSnapshot;
import com.interswitch.walletapp.fraud.PrefetchedFraudDataProvider;
import com.interswitch.walletapp.fraud.UserIpHistoryService;
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

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudDao fraudDao;
    private final FraudAttemptLoggerService fraudLogger;
    private final FraudConsequenceService fraudConsequenceService;
    private final FraudEvaluator fraudEvaluator;
    private final PrefetchedFraudDataProvider fraudDataProvider;
    private final UserIpHistoryService userIpHistoryService;

    public FraudStatus evaluate(FraudEvaluationContext ctx) {
        String cardHash = DigestUtils.sha256Hex(ctx.cardNumber());

        try {
            fraudDataProvider.prefetch(ctx.accountNumber(), cardHash, ctx.ipAddress());

            Set<String> recentIps = userIpHistoryService.getRecentIps(ctx.accountNumber());

            FraudContext fraudContext = FraudContext.builder()
                    .transactionId(ctx.transactionId())
                    .accountIdentifier(ctx.accountNumber())
                    .cardHash(cardHash)
                    .ipAddress(ctx.ipAddress())
                    .amount(ctx.amount())
                    .currency(ctx.currency())
                    .transactionTime(ctx.transactionTime().toInstant())
                    .lastKnownIpAddresses(recentIps)
                    .build();

            FraudResult result = fraudEvaluator.evaluate(fraudContext);

            FraudStatus status = mapDecision(result.decision());
            List<String> flags = extractFlags(result);

            FraudDataSnapshot snapshot = fraudDataProvider.getSnapshot();
            Long merchantId = snapshot != null ? snapshot.merchantId() : null;

            userIpHistoryService.recordIpAsync(ctx.accountNumber(), ctx.ipAddress());

            fraudLogger.logAttempt(cardHash, ctx, merchantId, status, flags);

            if (status != FraudStatus.CLEAN) {
                applyConsequences(ctx, cardHash, status, flags);
            }

            return status;
        } finally {
            fraudDataProvider.clear();
        }
    }

    private FraudStatus mapDecision(FraudDecision decision) {
        return switch (decision) {
            case ALLOW -> FraudStatus.CLEAN;
            case REVIEW -> FraudStatus.SUSPICIOUS;
            case BLOCK -> FraudStatus.BLOCKED;
        };
    }

    private List<String> extractFlags(FraudResult result) {
        return result.gateResults().stream()
                .filter(gr -> gr.score() > 0 || gr.hardBlock())
                .map(GateResult::gateName)
                .toList();
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
}
