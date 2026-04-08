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
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountDao {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbc;

    public Long insert(MapSqlParameterSource params) {
        return namedJdbc.queryForObject(
                "SELECT sp_account_insert(:merchantId, :accountNumber, :accountType, :currency, :accountStatus, :createdBy)",
                params, Long.class
        );
    }

    public Optional<AccountResponse> findById(Long accountId) {
        return namedJdbc.query(
                "SELECT * FROM sp_account_find_by_id(:id)",
                new MapSqlParameterSource("id", accountId),
                accountRowMapper()
        ).stream().findFirst();
    }

    public List<AccountResponse> findByMerchantWithCount(MapSqlParameterSource params, String sortField, String direction, long[] total) {
        params.addValue("sortField", sortField);
        params.addValue("sortDirection", direction);
        return namedJdbc.query(
                "SELECT * FROM sp_account_find_by_merchant(:merchantId, :limit, :offset, :sortField, :sortDirection)",
                params, (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return accountRowMapper().mapRow(rs, rowNum);
                });
    }

    public void updateStatus(MapSqlParameterSource params) {
        namedJdbc.queryForList(
                "SELECT sp_account_update_status(:id, :status, :updatedBy)",
                params
        );
    }

    public void softDelete(MapSqlParameterSource params) {
        namedJdbc.queryForList(
                "SELECT sp_account_soft_delete(:id, :deletedBy)",
                params
        );
    }

    public boolean exists(Long accountId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_account_exists(:id)",
                new MapSqlParameterSource("id", accountId),
                Boolean.class
        ));
    }

    public Optional<MerchantAccountValidation> getMerchantAccountValidation(Long merchantId) {
        return namedJdbc.query(
                "SELECT * FROM sp_account_get_merchant_validation(:merchantId)",
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
        Long result = namedJdbc.queryForObject(
                "SELECT sp_account_find_merchant_id_by_user_id(:userId)",
                new MapSqlParameterSource("userId", userId),
                Long.class
        );
        return Optional.ofNullable(result);
    }

    public String nextAccountNumber() {
        return jdbcTemplate.queryForObject("SELECT sp_account_next_number()", String.class);
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
