package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.TransactionType;
import com.interswitch.walletapp.models.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TransactionDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String INSERT_TRANSACTION = """
            INSERT INTO transactions (account_id, card_id, transfer_id, transaction_type,
                channel, amount, fee, currency, transaction_status, created_at, updated_at, created_by)
            VALUES (:accountId, :cardId, :transferId, :type,
                'TRANSFER', :amount, 0, :currency, 'SUCCESS', now(), now(), :createdBy)
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM transactions WHERE id = :id
            """;

    private static final String SELECT_BY_ACCOUNT = """
            SELECT *, COUNT(*) OVER() AS total_count
            FROM transactions
            WHERE account_id = :accountId
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String MERCHANT_OWNS_ACCOUNT = """
            SELECT EXISTS(
                SELECT 1 FROM accounts a
                JOIN merchants m ON a.merchant_id = m.id
                WHERE a.id = :accountId AND m.user_id = :userId AND a.deleted_at IS NULL
            )
            """;

    private static final String SUM_BY_CRITERIA = """
            SELECT COALESCE(SUM(amount), 0)
            FROM transactions
            WHERE account_id = :accountId
            AND transaction_type = :type
            AND transaction_status = :status
            AND created_at >= :from
            AND created_at <= :to
            """;

    public Long insert(MapSqlParameterSource params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(INSERT_TRANSACTION, params, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<TransactionResponse> findById(Long id) {
        return namedJdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", id), transactionRowMapper())
                .stream().findFirst();
    }

    public List<TransactionResponse> findByAccountWithCount(MapSqlParameterSource params, long[] total) {
        return namedJdbc.query(SELECT_BY_ACCOUNT, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return transactionRowMapper().mapRow(rs, rowNum);
        });
    }

    public boolean isMerchantOwnerOfAccount(Long accountId, Long userId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(MERCHANT_OWNS_ACCOUNT,
                new MapSqlParameterSource().addValue("accountId", accountId).addValue("userId", userId),
                Boolean.class));
    }

    public BigDecimal getSumByTypeAndStatus(Long accountId, String type, String status, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("type", type)
                .addValue("status", status)
                .addValue("from", from)
                .addValue("to", to);

        return namedJdbc.queryForObject(SUM_BY_CRITERIA, params, BigDecimal.class);
    }

    public int countTransactionsByTransferId(Long transferId) {
        Integer count = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE transfer_id = :transferId",
                new MapSqlParameterSource("transferId", transferId),
                Integer.class
        );
        return count != null ? count : 0;
    }

    private RowMapper<TransactionResponse> transactionRowMapper() {
        return (rs, _) -> new TransactionResponse(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getObject("card_id") != null ? rs.getLong("card_id") : null,
                rs.getObject("transfer_id") != null ? rs.getLong("transfer_id") : null,
                TransactionType.valueOf(rs.getString("transaction_type")),
                rs.getString("channel"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("fee"),
                rs.getString("currency"),
                rs.getString("transaction_status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}