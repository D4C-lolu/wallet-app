package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.AccountStatus;
import com.interswitch.walletapp.models.enums.AccountType;
import com.interswitch.walletapp.models.response.AccountResponse;
import com.interswitch.walletapp.models.projections.MerchantAccountValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountDao {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String INSERT_ACCOUNT = """
            INSERT INTO accounts (merchant_id, account_number, account_type, currency,
                balance, ledger_balance, account_status, created_at, updated_at, created_by)
            VALUES (:merchantId, :accountNumber, :accountType, :currency,
                0, 0, :accountStatus, now(), now(), :createdBy)
            """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM accounts WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String SELECT_BY_MERCHANT = """
            SELECT *, COUNT(*) OVER() AS total_count
            FROM accounts
            WHERE merchant_id = :merchantId AND deleted_at IS NULL
            ORDER BY %s %s
            LIMIT :limit OFFSET :offset
            """;

    private static final String UPDATE_STATUS = """
            UPDATE accounts SET account_status = :status, updated_at = now(), updated_by = :updatedBy
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String SOFT_DELETE = """
            UPDATE accounts SET deleted_at = now(), deleted_by = :deletedBy, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String MERCHANT_ACCOUNT_VALIDATION = """
            SELECT
                m.id,
                m.kyc_status,
                m.tier,
                tc.max_accounts,
                COUNT(a.id) AS current_account_count
            FROM merchants m
            LEFT JOIN tier_configs tc ON tc.tier = m.tier
            LEFT JOIN accounts a ON a.merchant_id = m.id AND a.deleted_at IS NULL
            WHERE m.id = :merchantId AND m.deleted_at IS NULL
            GROUP BY m.id, m.kyc_status, m.tier, tc.max_accounts
            """;

    private static final String EXISTS_ACCOUNT = """
            SELECT COUNT(*) FROM accounts WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String MERCHANT_ID_BY_USER = """
            SELECT id FROM merchants WHERE user_id = :userId AND deleted_at IS NULL
            """;

    private static final String NEXT_ACCOUNT_NUMBER = """
            SELECT nextval('account_number_seq')
            """;

    public Long insert(MapSqlParameterSource params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(INSERT_ACCOUNT, params, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<AccountResponse> findById(Long accountId) {
        return namedJdbc.query(
                SELECT_BY_ID,
                new MapSqlParameterSource("id", accountId),
                accountRowMapper()
        ).stream().findFirst();
    }

    public List<AccountResponse> findByMerchantWithCount(MapSqlParameterSource params, String sortField, String direction, long[] total) {
        String query = SELECT_BY_MERCHANT.formatted(sortField, direction);
        return namedJdbc.query(query, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return accountRowMapper().mapRow(rs, rowNum);
        });
    }

    public void updateStatus(MapSqlParameterSource params) {
        namedJdbc.update(UPDATE_STATUS, params);
    }

    public void softDelete(MapSqlParameterSource params) {
        namedJdbc.update(SOFT_DELETE, params);
    }

    public boolean exists(Long accountId) {
        Integer count = namedJdbc.queryForObject(
                EXISTS_ACCOUNT,
                new MapSqlParameterSource("id", accountId),
                Integer.class
        );
        return count != null && count > 0;
    }

    public Optional<MerchantAccountValidation> getMerchantAccountValidation(Long merchantId) {
        return namedJdbc.query(
                MERCHANT_ACCOUNT_VALIDATION,
                new MapSqlParameterSource("merchantId", merchantId),
                (rs, _) -> new MerchantAccountValidation(
                        rs.getLong("id"),
                        rs.getString("kyc_status"),
                        rs.getString("tier"),
                        rs.getInt("max_accounts"),
                        rs.getInt("current_account_count")
                )
        ).stream().findFirst();
    }

    public Optional<Long> findMerchantIdByUserId(Long userId) {
        return namedJdbc.query(
                MERCHANT_ID_BY_USER,
                new MapSqlParameterSource("userId", userId),
                (rs, _) -> rs.getLong("id")
        ).stream().findFirst();
    }

    public String nextAccountNumber() {
        return jdbcTemplate.queryForObject(NEXT_ACCOUNT_NUMBER, String.class);
    }

    private RowMapper<AccountResponse> accountRowMapper() {
        return (rs, _) -> new AccountResponse(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("account_number"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getString("currency"),
                rs.getBigDecimal("balance"),
                rs.getBigDecimal("ledger_balance"),
                AccountStatus.valueOf(rs.getString("account_status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}