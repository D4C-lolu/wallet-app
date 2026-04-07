package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.projections.BalanceMismatch;
import com.interswitch.walletapp.models.projections.StuckTransfer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class MaintenanceDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    // SELECTS Transactions older than 5 mins
    private static final String SELECT_STUCK_TRANSFERS = """
            SELECT id, from_account_id, to_account_id, amount
            FROM transfers
            WHERE transfer_status = 'PENDING'
            AND created_at < DATEADD('SECOND', -300, NOW())
            """;

    private static final String SELECT_BALANCE_MISMATCHES = """
            SELECT a.id, a.balance AS stored_balance,
                (SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
                 FROM transactions t WHERE t.account_id = a.id AND t.transaction_status = 'SUCCESS') AS calculated_balance
            FROM accounts a
            WHERE a.deleted_at IS NULL
            GROUP BY a.id, a.balance
            HAVING a.balance <> (
                SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
                FROM transactions t WHERE t.account_id = a.id AND t.transaction_status = 'SUCCESS'
            )
            """;

    private static final String RECALCULATE_ALL_BALANCES = """
            UPDATE accounts a
            SET balance = (
                SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
                FROM transactions t WHERE t.account_id = a.id AND t.transaction_status = 'SUCCESS'
            ),
            ledger_balance = (
                SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
                FROM transactions t WHERE t.account_id = a.id AND t.transaction_status IN ('SUCCESS', 'PENDING')
            ),
            updated_at = NOW()
            WHERE a.deleted_at IS NULL
            """;

    // 1. Mark transfers as SUCCESS if they have 2+ transactions
    private static final String BATCH_MARK_SUCCESS = """
            UPDATE transfers t
            SET t.transfer_status = 'SUCCESS', t.updated_at = NOW()
            WHERE t.transfer_status = 'PENDING'
            AND t.created_at < DATEADD('MINUTE', -5, NOW())
            AND (SELECT COUNT(*) FROM transactions tx WHERE tx.transfer_id = t.id) >= 2
            """;

    // 2. Mark transfers as FAILED if they have < 2 transactions
    private static final String BATCH_MARK_FAILED = """
            UPDATE transfers t
            SET t.transfer_status = 'FAILED', t.updated_at = NOW()
            WHERE t.transfer_status = 'PENDING'
            AND t.created_at < DATEADD('MINUTE', -5, NOW())
            AND (SELECT COUNT(*) FROM transactions tx WHERE tx.transfer_id = t.id) < 2
            """;

    public int bulkResolveSuccessfulTransfers() {
        return namedJdbc.update(BATCH_MARK_SUCCESS, new MapSqlParameterSource());
    }

    public int bulkResolveFailedTransfers() {
        return namedJdbc.update(BATCH_MARK_FAILED, new MapSqlParameterSource());
    }

    public List<StuckTransfer> findStuckTransfers() {
        return namedJdbc.query(SELECT_STUCK_TRANSFERS, (rs, _) -> new StuckTransfer(
                rs.getLong("id"),
                rs.getLong("from_account_id"),
                rs.getLong("to_account_id"),
                rs.getBigDecimal("amount")
        ));
    }

    public List<BalanceMismatch> findBalanceMismatches() {
        return namedJdbc.query(SELECT_BALANCE_MISMATCHES, (rs, _) -> new BalanceMismatch(
                rs.getLong("id"),
                rs.getBigDecimal("stored_balance"),
                rs.getBigDecimal("calculated_balance")
        ));
    }

    public void recalculateAllBalances() {
        namedJdbc.update(RECALCULATE_ALL_BALANCES, new MapSqlParameterSource());
    }
}