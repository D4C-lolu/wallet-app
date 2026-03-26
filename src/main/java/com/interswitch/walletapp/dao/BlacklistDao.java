package com.interswitch.walletapp.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BlacklistDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String IS_BLACKLISTED = """
            SELECT EXISTS (
                SELECT 1 FROM merchant_blacklist
                WHERE merchant_id = :merchantId
                AND lifted_at IS NULL
            )
            """;

    public boolean isActivelyBlacklisted(Long merchantId) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                IS_BLACKLISTED,
                new MapSqlParameterSource("merchantId", merchantId),
                Boolean.class
        ));
    }
}