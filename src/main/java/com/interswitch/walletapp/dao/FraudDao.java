package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.FraudStatus;
import com.interswitch.walletapp.models.projections.FraudAttemptRecord;
import com.interswitch.walletapp.models.response.FraudAttemptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
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

    private static final String INSERT_FRAUD_ATTEMPT = """
            INSERT INTO fraud_attempts (card_hash, merchant_id, ip_address, amount, currency, status, flags, created_at)
            VALUES (:cardHash, :merchantId, :ipAddress, :amount, :currency, :status, :flags, now())
            """;

    private static final String CARD_VELOCITY_COUNT = """
            SELECT COUNT(*) FROM fraud_attempts
            WHERE card_hash = :cardHash
            AND created_at >= :since
            """;

    private static final String MERCHANT_SINGLE_LIMIT = """
            SELECT tc.single_transaction_limit
            FROM tier_configs tc
            JOIN merchants m ON m.tier = tc.tier
            WHERE m.id = :merchantId
            AND m.deleted_at IS NULL
            """;

    private static final String SELECT_ALL_FRAUD_ATTEMPTS = """
            SELECT * FROM fraud_attempts
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_ALL_FRAUD_ATTEMPTS = """
            SELECT COUNT(*) FROM fraud_attempts
            """;

    public void insertFraudAttempt(FraudAttemptRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cardHash", record.cardHash())
                .addValue("merchantId", record.merchantId())
                .addValue("ipAddress", record.ipAddress())
                .addValue("amount", record.amount())
                .addValue("currency", record.currency())
                .addValue("status", record.status().name())
                .addValue("flags", record.flags().toArray(new String[0]));

        namedJdbc.update(INSERT_FRAUD_ATTEMPT, params);
    }

    public int getCardVelocityCount(String cardHash, OffsetDateTime since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cardHash", cardHash)
                .addValue("since", since);
        Integer count = namedJdbc.queryForObject(CARD_VELOCITY_COUNT, params, Integer.class);
        return count != null ? count : 0;
    }

    public Optional<BigDecimal> getMerchantSingleLimit(Long merchantId) {
        try {
            BigDecimal limit = namedJdbc.queryForObject(
                    MERCHANT_SINGLE_LIMIT,
                    new MapSqlParameterSource("merchantId", merchantId),
                    BigDecimal.class
            );
            return Optional.ofNullable(limit);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<FraudAttemptResponse> getFraudAttempts(int limit, int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return namedJdbc.query(SELECT_ALL_FRAUD_ATTEMPTS, params, fraudAttemptRowMapper());
    }

    public long countFraudAttempts() {
        Long count = namedJdbc.queryForObject(
                COUNT_ALL_FRAUD_ATTEMPTS,
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