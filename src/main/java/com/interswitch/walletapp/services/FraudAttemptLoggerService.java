package com.interswitch.walletapp.services;

import com.interswitch.walletapp.dao.FraudDao;
import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudAttemptRecord;
import com.interswitch.walletapp.models.projections.FraudEvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudAttemptLoggerService {

    private final FraudDao fraudDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttempt(String cardHash, FraudEvaluationContext ctx, Long merchantId, FraudStatus status, List<String> flags) {
        try {
            fraudDao.insertFraudAttempt(new FraudAttemptRecord(
                    cardHash,
                    merchantId,
                    ctx.ipAddress(),
                    ctx.amount(),
                    ctx.currency(),
                    status,
                    flags
            ));
        } catch (Exception e) {
            log.error("Failed to log fraud attempt for account={} ip={}", ctx.accountNumber(), ctx.ipAddress(), e);
        }
    }
}