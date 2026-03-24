package com.interswitch.walletapp.cron;

import com.interswitch.walletapp.dao.MaintenanceDao;
import com.interswitch.walletapp.dao.TransactionDao;
import com.interswitch.walletapp.dao.TransferDao;
import com.interswitch.walletapp.models.projections.BalanceMismatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferRecoveryJob {

    private final MaintenanceDao maintenanceDao;

    @Scheduled(cron = "0 0 0,12 * * *")
    @Transactional
    public void recoverStuckTransfers() {
        log.info("Starting batch transfer recovery process...");

        try {
            int recoveredCount = maintenanceDao.bulkResolveSuccessfulTransfers();
            if (recoveredCount > 0) {
                log.warn("Successfully recovered {} stuck transfers to SUCCESS status", recoveredCount);
            }

            int failedCount = maintenanceDao.bulkResolveFailedTransfers();
            if (failedCount > 0) {
                log.warn("Marked {} orphaned transfers as FAILED", failedCount);
            }

            if (recoveredCount == 0 && failedCount == 0) {
                log.info("Recovery check complete: No stuck transfers found.");
            }

        } catch (Exception e) {
            log.error("Critical failure during transfer recovery job", e);
        }
    }

    @Scheduled(cron = "0 30 0,12 * * *")
    @Transactional
    public void reconcileBalances() {
        log.info("Starting balance reconciliation job");

        List<BalanceMismatch> mismatches = maintenanceDao.findBalanceMismatches();

        if (mismatches.isEmpty()) {
            log.info("All account balances are consistent");
            return;
        }

        log.warn("Found {} mismatches. Example: Account {} stored {} vs calculated {}", 
            mismatches.size(), mismatches.getFirst().accountId(), 
            mismatches.getFirst().storedBalance(), mismatches.getFirst().calculatedBalance());

        maintenanceDao.recalculateAllBalances();

        log.info("Balance reconciliation complete.");
    }
}