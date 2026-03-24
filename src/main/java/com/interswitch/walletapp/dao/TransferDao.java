package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.TransferStatus;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.models.response.TransferResponse;
import com.interswitch.walletapp.models.projections.TransferValidationResult;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TransferDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String TRANSFER_VALIDATION = """
            SELECT
                (SELECT COUNT(*) FROM transfers WHERE reference = :reference) AS reference_exists,
                fa.balance AS from_balance,
                fa.currency AS from_currency,
                fa.account_status AS from_status,
                ta.currency AS to_currency,
                ta.account_status AS to_status,
                tc.single_transaction_limit,
                tc.daily_transaction_limit,
                tc.monthly_transaction_limit
            FROM accounts fa
            JOIN accounts ta ON ta.id = :toAccountId AND ta.deleted_at IS NULL
            JOIN merchants m ON m.id = fa.merchant_id AND m.deleted_at IS NULL
            JOIN tier_config tc ON tc.tier = m.tier
            WHERE fa.id = :fromAccountId AND fa.deleted_at IS NULL
            """;

    private static final String SELECT_STUCK_TRANSFERS = """
        SELECT id, from_account_id, to_account_id, amount
        FROM transfers
        WHERE transfer_status = 'PENDING'
        AND created_at < DATEADD('SECOND', -300, NOW())
        """;

    public Optional<TransferValidationResult> getTransferValidation(Long fromId, Long toId, String ref) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromAccountId", fromId)
                .addValue("toAccountId", toId)
                .addValue("reference", ref);

        return namedJdbc.query(TRANSFER_VALIDATION, params, (rs, _) -> new TransferValidationResult(
                rs.getInt("reference_exists"),
                rs.getBigDecimal("from_balance"),
                rs.getString("from_currency"),
                rs.getString("from_status"),
                rs.getString("to_currency"),
                rs.getString("to_status"),
                rs.getBigDecimal("single_transaction_limit"),
                rs.getBigDecimal("daily_transaction_limit"),
                rs.getBigDecimal("monthly_transaction_limit")
        )).stream().findFirst();
    }

    public Long insert(TransferRequest request, Long createdBy) {
        String sql = """
            INSERT INTO transfers (reference, from_account_id, to_account_id, amount,
                currency, transfer_status, description, created_at, updated_at, created_by)
            VALUES (:reference, :fromAccountId, :toAccountId, :amount,
                :currency, 'PENDING', :description, now(), now(), :createdBy)
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(sql, new MapSqlParameterSource()
                .addValue("reference", request.reference())
                .addValue("fromAccountId", request.fromAccountId())
                .addValue("toAccountId", request.toAccountId())
                .addValue("amount", request.amount())
                .addValue("currency", request.currency())
                .addValue("description", request.description())
                .addValue("createdBy", createdBy), keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void debitAccount(Long id, BigDecimal amount) {
        namedJdbc.update("UPDATE accounts SET balance = balance - :amount, ledger_balance = ledger_balance - :amount, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("amount", amount));
    }

    public void creditAccount(Long id, BigDecimal amount) {
        namedJdbc.update("UPDATE accounts SET balance = balance + :amount, ledger_balance = ledger_balance + :amount, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("amount", amount));
    }

    public void updateStatus(Long id, String status) {
        namedJdbc.update("UPDATE transfers SET transfer_status = :status, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("status", status));
    }

    public boolean isMerchantOwnerOfAccount(Long accountId, Long userId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM accounts a JOIN merchants m ON a.merchant_id = m.id WHERE a.id = :accountId AND m.user_id = :userId)";
        return Boolean.TRUE.equals(namedJdbc.queryForObject(sql, new MapSqlParameterSource("accountId", accountId).addValue("userId", userId), Boolean.class));
    }

    public Optional<TransferResponse> findById(Long id) {
        return namedJdbc.query("SELECT * FROM transfers WHERE id = :id", new MapSqlParameterSource("id", id), transferRowMapper()).stream().findFirst();
    }

    public List<TransferResponse> findByAccountWithPage(Long accountId, int limit, int offset, long[] total) {
        String sql = "SELECT *, COUNT(*) OVER() AS total_count FROM transfers WHERE from_account_id = :accountId OR to_account_id = :accountId ORDER BY created_at DESC LIMIT :limit OFFSET :offset";
        return namedJdbc.query(sql, new MapSqlParameterSource("accountId", accountId).addValue("limit", limit).addValue("offset", offset), (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return transferRowMapper().mapRow(rs, rowNum);
        });
    }

    public List<Map<String, Object>> findStuckTransfers() {
        return namedJdbc.queryForList(SELECT_STUCK_TRANSFERS, new MapSqlParameterSource());
    }

    public void updateStatus(Long id, TransferStatus status) {
        namedJdbc.update("UPDATE transfers SET transfer_status = :status, updated_at = NOW() WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("status", status.name()));
    }

    private RowMapper<TransferResponse> transferRowMapper() {
        return (rs, _) -> new TransferResponse(
                rs.getLong("id"), rs.getString("reference"), rs.getLong("from_account_id"),
                rs.getLong("to_account_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
                TransferStatus.valueOf(rs.getString("transfer_status")), rs.getString("description"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}