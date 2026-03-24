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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CardDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String INSERT_CARD = """
        INSERT INTO cards (card_number, card_hash, account_id, card_type, scheme,
            expiry_month, expiry_year, card_status, created_at, updated_at, created_by)
        VALUES (:cardNumber, :cardHash, :accountId, :cardType, :scheme,
            :expiryMonth, :expiryYear, :cardStatus, now(), now(), :createdBy)
        """;

    private static final String SELECT_BY_ID = """
            SELECT * FROM cards WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String SELECT_BY_ACCOUNT = """
            SELECT *, COUNT(*) OVER() AS total_count
            FROM cards WHERE account_id = :accountId AND deleted_at IS NULL
            ORDER BY %s %s
            LIMIT :limit OFFSET :offset
            """;

    private static final String UPDATE_STATUS = """
            UPDATE cards SET card_status = :status, updated_at = now(), updated_by = :updatedBy
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String SOFT_DELETE = """
            UPDATE cards SET deleted_at = now(), deleted_by = :deletedBy, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String MERCHANT_OWNS_CARD = """
        SELECT EXISTS(
            SELECT 1 FROM cards c
            JOIN accounts a ON c.account_id = a.id
            JOIN merchants m ON a.merchant_id = m.id
            WHERE c.id = :cardId AND m.user_id = :userId AND c.deleted_at IS NULL
        )
        """;

    private static final String BLOCK_CARD = """
        UPDATE cards SET card_status = 'BLOCKED', updated_at = now(), updated_by = :updatedBy
        WHERE id = :id AND deleted_at IS NULL AND card_status != 'BLOCKED'
        """;

    private static final String MERCHANT_ID_AND_ACCOUNT_VALIDATION = """
            SELECT m.id AS merchant_id
            FROM merchants m
            JOIN accounts a ON a.merchant_id = m.id
            WHERE m.user_id = :userId
            AND a.id = :accountId
            AND m.deleted_at IS NULL
            AND a.deleted_at IS NULL
        """;

    private static final String CARD_CREATION_VALIDATION = """
        SELECT
            a.id AS account_id,
            m.id AS merchant_id,
            m.tier,
            tc.max_cards,
            COUNT(c.id) AS current_card_count,
            EXISTS(SELECT 1 FROM cards WHERE card_hash = :cardHash AND deleted_at IS NULL) AS card_hash_exists
        FROM accounts a
        JOIN merchants m ON a.merchant_id = m.id
        JOIN tier_config tc ON tc.tier = m.tier
        LEFT JOIN cards c ON c.account_id = a.id AND c.deleted_at IS NULL
        WHERE a.id = :accountId AND a.deleted_at IS NULL
        GROUP BY a.id, m.id, m.tier, tc.max_cards
        """;

    private static final String EXISTS_CARD = """
        SELECT COUNT(*) FROM cards WHERE id = :id AND deleted_at IS NULL
        """;

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

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(INSERT_CARD, params, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<CardResponse> findById(Long cardId) {
        return namedJdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", cardId), cardRowMapper())
                .stream().findFirst();
    }

    public List<CardResponse> findByAccountWithCount(MapSqlParameterSource params, String sortField, String direction, long[] total) {
        String query = SELECT_BY_ACCOUNT.formatted(sortField, direction);
        return namedJdbc.query(query, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return cardRowMapper().mapRow(rs, rowNum);
        });
    }

    public boolean exists(Long cardId) {
        Integer count = namedJdbc.queryForObject(EXISTS_CARD, new MapSqlParameterSource("id", cardId), Integer.class);
        return count != null && count > 0;
    }

    public void blockCard(Long cardId, Long updatedBy) {
        namedJdbc.update(BLOCK_CARD, new MapSqlParameterSource()
                .addValue("id", cardId)
                .addValue("updatedBy", updatedBy));
    }

    public boolean isMerchantOwnerOfCard(Long cardId, Long userId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(MERCHANT_OWNS_CARD,
                new MapSqlParameterSource().addValue("cardId", cardId).addValue("userId", userId),
                Boolean.class));
    }

    public Optional<MerchantAccountLink> validateMerchantAccountOwnership(Long userId, Long accountId) {
        return namedJdbc.query(MERCHANT_ID_AND_ACCOUNT_VALIDATION,
                new MapSqlParameterSource().addValue("userId", userId).addValue("accountId", accountId),
                (rs, _) -> new MerchantAccountLink(rs.getLong("merchant_id"))
        ).stream().findFirst();
    }

    public Optional<CardValidationResult> getCardCreationValidation(Long accountId, String cardHash) {
        return namedJdbc.query(CARD_CREATION_VALIDATION,
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
        namedJdbc.update(UPDATE_STATUS, new MapSqlParameterSource()
                .addValue("id", cardId)
                .addValue("status", status)
                .addValue("updatedBy", updatedBy));
    }

    public void softDelete(Long cardId, Long deletedBy) {
        namedJdbc.update(SOFT_DELETE, new MapSqlParameterSource()
                .addValue("id", cardId)
                .addValue("deletedBy", deletedBy));
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