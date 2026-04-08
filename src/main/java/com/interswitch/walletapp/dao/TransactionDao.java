package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.TransactionType;
import com.interswitch.walletapp.models.response.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TransactionDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    public Long insert(MapSqlParameterSource params) {
        return namedJdbc.queryForObject(
                "SELECT sp_transaction_insert(:accountId, :cardId, :transferId, :type, :amount, :currency, :createdBy)",
                params, Long.class
        );
    }

    public Optional<TransactionResponse> findById(Long id) {
        return namedJdbc.query(
                "SELECT * FROM sp_transaction_find_by_id(:id)",
                new MapSqlParameterSource("id", id),
                transactionRowMapper()
        ).stream().findFirst();
    }

    public List<TransactionResponse> findByAccountWithCount(MapSqlParameterSource params, long[] total) {
        return namedJdbc.query(
                "SELECT * FROM sp_transaction_find_by_account(:accountId, :limit, :offset)",
                params, (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return transactionRowMapper().mapRow(rs, rowNum);
                });
    }

    public boolean isMerchantOwnerOfAccount(Long accountId, Long userId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_transaction_is_merchant_owner(:accountId, :userId)",
                new MapSqlParameterSource().addValue("accountId", accountId).addValue("userId", userId),
                Boolean.class
        ));
    }

    public BigDecimal getSumByTypeAndStatus(Long accountId, String type, String status, OffsetDateTime from, OffsetDateTime to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("type", type)
                .addValue("status", status)
                .addValue("from", from)
                .addValue("to", to);

        return namedJdbc.queryForObject(
                "SELECT sp_transaction_sum_by_criteria(:accountId, :type, :status, :from, :to)",
                params, BigDecimal.class
        );
    }

    public int countTransactionsByTransferId(Long transferId) {
        Integer count = namedJdbc.queryForObject(
                "SELECT sp_transaction_count_by_transfer_id(:transferId)",
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
