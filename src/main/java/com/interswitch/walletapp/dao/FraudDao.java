package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudAttemptRecord;
import com.interswitch.walletapp.models.response.FraudAttemptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FraudDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    public record FraudEvaluationData(
            boolean isBlacklisted,
            int velocityCount,
            BigDecimal transactionLimit,
            Long merchantId
    ) {}

    public FraudEvaluationData getEvaluationData(String accountNumber, String cardHash, OffsetDateTime since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountNumber", accountNumber)
                .addValue("cardHash", cardHash)
                .addValue("since", since);

        return namedJdbc.queryForObject(
                "SELECT * FROM sp_fraud_get_evaluation_data(:accountNumber, :cardHash, :since)",
                params,
                (rs, rowNum) -> new FraudEvaluationData(
                        rs.getBoolean("is_blacklisted"),
                        rs.getInt("velocity_count"),
                        rs.getBigDecimal("transaction_limit"),
                        rs.getObject("merchant_id", Long.class)
                )
        );
    }

    public void insertFraudAttempt(FraudAttemptRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cardHash", record.cardHash())
                .addValue("merchantId", record.merchantId())
                .addValue("ipAddress", record.ipAddress())
                .addValue("amount", record.amount())
                .addValue("currency", record.currency())
                .addValue("status", record.status().name())
                .addValue("flags", record.flags().toArray(new String[0]));

        namedJdbc.queryForList(
                "SELECT sp_fraud_insert_attempt(:cardHash, :merchantId, :ipAddress, :amount, :currency, :status, :flags)",
                params
        );
    }

    public int getCardVelocityCount(String cardHash, OffsetDateTime since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cardHash", cardHash)
                .addValue("since", since);
        Integer count = namedJdbc.queryForObject(
                "SELECT sp_fraud_get_card_velocity_count(:cardHash, :since)",
                params, Integer.class
        );
        return count != null ? count : 0;
    }

    public Optional<BigDecimal> getMerchantSingleLimitByAccount(String accountNumber) {
        BigDecimal limit = namedJdbc.queryForObject(
                "SELECT sp_fraud_get_merchant_single_limit_by_account(:accountNumber)",
                new MapSqlParameterSource("accountNumber", accountNumber),
                BigDecimal.class
        );
        return Optional.ofNullable(limit);
    }

    public List<FraudAttemptResponse> getFraudAttempts(int limit, int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return namedJdbc.query(
                "SELECT * FROM sp_fraud_get_attempts(:limit, :offset)",
                params, fraudAttemptRowMapper()
        );
    }

    public long countFraudAttempts() {
        Long count = namedJdbc.queryForObject(
                "SELECT sp_fraud_count_attempts()",
                new MapSqlParameterSource(),
                Long.class
        );
        return count != null ? count : 0;
    }

    private RowMapper<FraudAttemptResponse> fraudAttemptRowMapper() {
        return (rs, rowNum) -> {
            Array flagsArray = rs.getArray("flags");
            List<String> flags = flagsArray != null
                    ? Arrays.asList((String[]) flagsArray.getArray())
                    : List.of();

            return new FraudAttemptResponse(
                    rs.getLong("id"),
                    rs.getString("card_hash"),
                    rs.getLong("merchant_id"),
                    rs.getString("ip_address"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency"),
                    FraudStatus.valueOf(rs.getString("status")),
                    flags,
                    rs.getObject("created_at", OffsetDateTime.class)
            );
        };
    }
}
