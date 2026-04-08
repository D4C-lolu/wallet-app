package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.*;
import com.interswitch.walletapp.models.request.CreateCardRequest;
import com.interswitch.walletapp.models.response.CardResponse;
import com.interswitch.walletapp.models.projections.CardValidationResult;
import com.interswitch.walletapp.models.projections.MerchantAccountLink;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CardDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    public Long insert(CreateCardRequest request, String cardHash, String maskedCardNumber, Long createdBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cardNumber", maskedCardNumber)
                .addValue("cardHash", cardHash)
                .addValue("accountId", request.accountId())
                .addValue("cardType", request.cardType().name())
                .addValue("scheme", request.scheme().name())
                .addValue("expiryMonth", request.expiryMonth())
                .addValue("expiryYear", request.expiryYear())
                .addValue("cardStatus", CardStatus.ACTIVE.name())
                .addValue("createdBy", createdBy);

        return namedJdbc.queryForObject(
                "SELECT sp_card_insert(:cardNumber, :cardHash, :accountId, :cardType, :scheme, :expiryMonth::smallint, :expiryYear::smallint, :cardStatus, :createdBy)",
                params, Long.class
        );
    }

    public Optional<CardResponse> findById(Long cardId) {
        return namedJdbc.query(
                "SELECT * FROM sp_card_find_by_id(:id)",
                new MapSqlParameterSource("id", cardId),
                cardRowMapper()
        ).stream().findFirst();
    }

    public List<CardResponse> findByAccountWithCount(MapSqlParameterSource params, String sortField, String direction, long[] total) {
        params.addValue("sortField", sortField);
        params.addValue("sortDirection", direction);
        return namedJdbc.query(
                "SELECT * FROM sp_card_find_by_account(:accountId, :limit, :offset, :sortField, :sortDirection)",
                params, (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return cardRowMapper().mapRow(rs, rowNum);
                });
    }

    public boolean exists(Long cardId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_card_exists(:id)",
                new MapSqlParameterSource("id", cardId),
                Boolean.class
        ));
    }

    public void blockCard(Long cardId, Long updatedBy) {
        namedJdbc.queryForList(
                "SELECT sp_card_block(:id, :updatedBy)",
                new MapSqlParameterSource()
                        .addValue("id", cardId)
                        .addValue("updatedBy", updatedBy)
        );
    }

    public boolean isMerchantOwnerOfCard(Long cardId, Long userId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_card_is_merchant_owner(:cardId, :userId)",
                new MapSqlParameterSource().addValue("cardId", cardId).addValue("userId", userId),
                Boolean.class
        ));
    }

    public Optional<MerchantAccountLink> validateMerchantAccountOwnership(Long userId, Long accountId) {
        return namedJdbc.query(
                "SELECT * FROM sp_card_validate_merchant_account(:userId, :accountId)",
                new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
                (rs, _) -> new MerchantAccountLink(rs.getLong("merchant_id"))
        ).stream().findFirst();
    }

    public Optional<CardValidationResult> getCardCreationValidation(Long accountId, String cardHash) {
        return namedJdbc.query(
                "SELECT * FROM sp_card_get_creation_validation(:accountId, :cardHash)",
                new MapSqlParameterSource().addValue("accountId", accountId).addValue("cardHash", cardHash),
                (rs, _) -> new CardValidationResult(
                        rs.getLong("account_id"),
                        rs.getLong("merchant_id"),
                        rs.getString("tier"),
                        rs.getInt("max_cards"),
                        rs.getInt("current_card_count"),
                        rs.getBoolean("card_hash_exists")
                )).stream().findFirst();
    }

    public void updateStatus(Long cardId, String status, Long updatedBy) {
        namedJdbc.queryForList(
                "SELECT sp_card_update_status(:id, :status, :updatedBy)",
                new MapSqlParameterSource()
                        .addValue("id", cardId)
                        .addValue("status", status)
                        .addValue("updatedBy", updatedBy)
        );
    }

    public void softDelete(Long cardId, Long deletedBy) {
        namedJdbc.queryForList(
                "SELECT sp_card_soft_delete(:id, :deletedBy)",
                new MapSqlParameterSource()
                        .addValue("id", cardId)
                        .addValue("deletedBy", deletedBy)
        );
    }

    public boolean blockByHash(String cardHash) {
        return namedJdbc.query(
                "SELECT * FROM sp_card_block_by_hash(:cardHash)",
                new MapSqlParameterSource("cardHash", cardHash),
                (rs, _) -> rs.getBoolean("was_blocked")
        ).stream().findFirst().orElse(false);
    }

    public Optional<Long> findIdByHash(String cardHash) {
        Long result = namedJdbc.queryForObject(
                "SELECT sp_card_find_id_by_hash(:cardHash)",
                new MapSqlParameterSource("cardHash", cardHash),
                Long.class
        );
        return Optional.ofNullable(result);
    }

    public boolean isActiveByHash(String cardHash) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_card_is_active_by_hash(:cardHash)",
                new MapSqlParameterSource("cardHash", cardHash),
                Boolean.class
        ));
    }

    private RowMapper<CardResponse> cardRowMapper() {
        return (rs, _) -> new CardResponse(
                rs.getLong("id"),
                rs.getLong("account_id"),
                rs.getString("card_number"),
                CardType.valueOf(rs.getString("card_type")),
                CardScheme.valueOf(rs.getString("scheme")),
                rs.getInt("expiry_month"),
                rs.getInt("expiry_year"),
                CardStatus.valueOf(rs.getString("card_status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
