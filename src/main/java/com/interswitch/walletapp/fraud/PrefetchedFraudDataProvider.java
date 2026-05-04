package com.interswitch.walletapp.fraud;

import com.interswitch.verveguard.api.GeoIpService;
import com.interswitch.verveguard.core.AbstractFraudDataProvider;
import com.interswitch.walletapp.dao.FraudDao;
import com.interswitch.walletapp.services.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Component
public class PrefetchedFraudDataProvider extends AbstractFraudDataProvider {

    private final FraudDao fraudDao;
    private final RateLimiterService rateLimiterService;

    @Value("${verveguard.velocity.window-seconds:60}")
    private int velocityWindowSeconds;

    private static final ThreadLocal<FraudDataSnapshot> SNAPSHOT_HOLDER = new ThreadLocal<>();

    public PrefetchedFraudDataProvider(GeoIpService geoIpService, FraudDao fraudDao, RateLimiterService rateLimiterService) {
        super(geoIpService);
        this.fraudDao = fraudDao;
        this.rateLimiterService = rateLimiterService;
    }

    public void prefetch(String accountNumber, String cardHash, String ipAddress) {
        OffsetDateTime since = OffsetDateTime.now().minusSeconds(velocityWindowSeconds);
        FraudDao.FraudEvaluationData dbData = fraudDao.getEvaluationData(accountNumber, cardHash, since);
        boolean isRateLimited = rateLimiterService.isRateLimited(ipAddress);

        FraudDataSnapshot snapshot = new FraudDataSnapshot(
                dbData.isBlacklisted(),
                isRateLimited,
                dbData.velocityCount(),
                dbData.transactionLimit(),
                dbData.merchantId()
        );
        SNAPSHOT_HOLDER.set(snapshot);
    }

    public FraudDataSnapshot getSnapshot() {
        return SNAPSHOT_HOLDER.get();
    }

    public void clear() {
        SNAPSHOT_HOLDER.remove();
    }

    @Override
    public boolean isBlacklisted(String accountId) {
        FraudDataSnapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot == null) {
            log.warn("No prefetched data available, falling back to DB query");
            return fraudDao.getEvaluationData(accountId, "", OffsetDateTime.now()).isBlacklisted();
        }
        return snapshot.isBlacklisted();
    }

    @Override
    public boolean isRateLimited(String ip) {
        FraudDataSnapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot == null) {
            return rateLimiterService.isRateLimited(ip);
        }
        return snapshot.isRateLimited();
    }

    @Override
    public int getVelocityCount(String cardHash, Duration window) {
        FraudDataSnapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot == null) {
            OffsetDateTime since = OffsetDateTime.now().minus(window);
            return fraudDao.getCardVelocityCount(cardHash, since);
        }
        return snapshot.velocityCount();
    }

    @Override
    public Optional<BigDecimal> getTransactionLimit(String accountId) {
        FraudDataSnapshot snapshot = SNAPSHOT_HOLDER.get();
        if (snapshot == null) {
            return fraudDao.getMerchantSingleLimitByAccount(accountId);
        }
        return Optional.ofNullable(snapshot.transactionLimit());
    }
}
