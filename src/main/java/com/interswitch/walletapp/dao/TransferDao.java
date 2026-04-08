package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.TransferStatus;
import com.interswitch.walletapp.models.request.TransferRequest;
import com.interswitch.walletapp.models.response.TransferResponse;
import com.interswitch.walletapp.models.projections.TransferValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TransferDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    public Optional<TransferValidationResult> getTransferValidation(String fromAccountNumber, String toAccountNumber, String ref) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromAccountNumber", fromAccountNumber)
                .addValue("toAccountNumber", toAccountNumber)
                .addValue("reference", ref);

        return namedJdbc.query(
                "SELECT * FROM sp_transfer_get_validation(:fromAccountNumber, :toAccountNumber, :reference)",
                params, (rs, _) -> new TransferValidationResult(
                        rs.getInt("reference_exists"),
                        rs.getLong("from_account_id"),
                        rs.getBigDecimal("from_balance"),
                        rs.getString("from_currency"),
                        rs.getString("from_status"),
                        rs.getLong("to_account_id"),
                        rs.getString("to_currency"),
                        rs.getString("to_status"),
                        rs.getBigDecimal("single_transaction_limit"),
                        rs.getBigDecimal("daily_transaction_limit"),
                        rs.getBigDecimal("monthly_transaction_limit")
                )).stream().findFirst();
    }

    public Long insert(Long fromAccountId, Long toAccountId, TransferRequest request, String reference, Long createdBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("reference", reference)
                .addValue("fromAccountId", fromAccountId)
                .addValue("toAccountId", toAccountId)
                .addValue("amount", request.amount())
                .addValue("currency", request.currency())
                .addValue("description", request.description())
                .addValue("createdBy", createdBy);

        return namedJdbc.queryForObject(
                "SELECT sp_transfer_insert(:reference, :fromAccountId, :toAccountId, :amount, :currency, :description, :createdBy)",
                params, Long.class
        );
    }

    public void debitAccount(Long id, BigDecimal amount) {
        namedJdbc.queryForList(
                "SELECT sp_transfer_debit_account(:id, :amount)",
                new MapSqlParameterSource("id", id).addValue("amount", amount)
        );
    }

    public void creditAccount(Long id, BigDecimal amount) {
        namedJdbc.queryForList(
                "SELECT sp_transfer_credit_account(:id, :amount)",
                new MapSqlParameterSource("id", id).addValue("amount", amount)
        );
    }

    public void updateStatus(Long id, String status) {
        namedJdbc.queryForList(
                "SELECT sp_transfer_update_status(:id, :status)",
                new MapSqlParameterSource("id", id).addValue("status", status)
        );
    }

    public boolean isMerchantOwnerOfAccount(Long accountId, Long userId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_transfer_is_merchant_owner(:accountId, :userId)",
                new MapSqlParameterSource("accountId", accountId).addValue("userId", userId),
                Boolean.class
        ));
    }

    public boolean isMerchantOwnerOfAccountByNumber(String accountNumber, Long userId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_transfer_is_merchant_owner_by_number(:accountNumber, :userId)",
                new MapSqlParameterSource("accountNumber", accountNumber).addValue("userId", userId),
                Boolean.class
        ));
    }

    public Optional<TransferResponse> findById(Long id) {
        return namedJdbc.query(
                "SELECT * FROM sp_transfer_find_by_id(:id)",
                new MapSqlParameterSource("id", id),
                transferRowMapper()
        ).stream().findFirst();
    }

    public List<TransferResponse> findByAccountWithPage(Long accountId, int limit, int offset, long[] total) {
        return namedJdbc.query(
                "SELECT * FROM sp_transfer_find_by_account_with_page(:accountId, :limit, :offset)",
                new MapSqlParameterSource("accountId", accountId).addValue("limit", limit).addValue("offset", offset),
                (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return transferRowMapper().mapRow(rs, rowNum);
                });
    }

    public List<Map<String, Object>> findStuckTransfers() {
        return namedJdbc.queryForList(
                "SELECT * FROM sp_transfer_find_stuck()",
                new MapSqlParameterSource()
        );
    }

    public void updateStatus(Long id, TransferStatus status) {
        namedJdbc.queryForList(
                "SELECT sp_transfer_update_status(:id, :status)",
                new MapSqlParameterSource("id", id).addValue("status", status.name())
        );
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
