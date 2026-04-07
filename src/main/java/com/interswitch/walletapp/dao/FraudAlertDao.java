package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.projections.CardOwnerInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FraudAlertDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String GET_CARD_OWNER_INFO = """
        SELECT c.id AS card_id,
               c.card_number,
               c.card_type,
               c.scheme,
               u.email
        FROM cards c
        JOIN accounts a ON c.account_id = a.id
        JOIN merchants m ON a.merchant_id = m.id
        JOIN users u ON m.user_id = u.id
        WHERE c.id = :cardId
          AND c.deleted_at IS NULL
        """;

    public Optional<CardOwnerInfo> getCardOwnerInfo(Long cardId) {
        return namedJdbc.query(GET_CARD_OWNER_INFO,
                new MapSqlParameterSource("cardId", cardId),
                (rs, _) -> new CardOwnerInfo(
                        rs.getLong("card_id"),
                        rs.getString("card_number"),
                        rs.getString("card_type"),
                        rs.getString("scheme"),
                        rs.getString("email")
                )).stream().findFirst();
    }
}
