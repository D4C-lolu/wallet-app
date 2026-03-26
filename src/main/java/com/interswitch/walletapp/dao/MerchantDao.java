package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.KycStatus;
import com.interswitch.walletapp.models.enums.MerchantStatus;
import com.interswitch.walletapp.models.enums.MerchantTier;
import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.response.MerchantResponse;
import com.interswitch.walletapp.models.projections.MerchantValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class MerchantDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String INSERT_MERCHANT = """
            INSERT INTO merchants (user_id, address, kyc_status, merchant_status, tier, created_at, updated_at, created_by)
            VALUES (:userId, :address, :kycStatus, :merchantStatus, :tier, now(), now(), :createdBy)
            """;

    private static final String SELECT_BY_ID = """
            SELECT m.*, u.firstname, u.lastname, u.email, u.phone, u.user_status
            FROM merchants m
            JOIN users u ON u.id = m.user_id
            WHERE m.id = :id AND m.deleted_at IS NULL
            """;

    private static final String SELECT_BASE = """
            SELECT m.*, u.firstname, u.lastname, u.email, u.phone, u.user_status,
                COUNT(*) OVER() AS total_count
            FROM merchants m
            JOIN users u ON u.id = m.user_id
            WHERE %s AND m.deleted_at IS NULL
            ORDER BY %s %s
            LIMIT :limit OFFSET :offset
            """;

    private static final String UPDATE_KYC_STATUS = """
            UPDATE merchants SET kyc_status = :kycStatus,
            updated_at = now(), updated_by = :updatedBy
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String UPDATE_MERCHANT_STATUS = """
            UPDATE merchants SET merchant_status = :merchantStatus,
            updated_at = now(), updated_by = :updatedBy
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String UPDATE_MERCHANT_STATUS_AND_KYC = """
            UPDATE merchants SET merchant_status = :merchantStatus,
            kyc_status = :kycStatus, updated_at = now(), updated_by = :updatedBy
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String CREATE_MERCHANT_VALIDATION = """
        SELECT
            (SELECT COUNT(*) FROM merchants WHERE user_id = :userId AND deleted_at IS NULL) > 0 AS merchant_exists,
            (SELECT COUNT(*) FROM users WHERE id = :userId AND deleted_at IS NULL) > 0 AS user_exists,
            (SELECT COUNT(*) FROM tier_configs WHERE tier = :tier) > 0 AS tier_exists
        """;

    private static final String UPDATE_STATUS = """
            UPDATE merchants SET status = :status, updated_by = :updatedBy, updated_at = NOW()
            WHERE id = :id
         """;
    private static final String UPDATE_KYC = """
            UPDATE merchants SET kyc_status = :kycStatus, updated_by = :updatedBy, updated_at = NOW()
            WHERE id = :id
         """;

    private static final String SELECT_BY_STATUS = """
        SELECT * FROM merchants WHERE status = :status
        """;

    private static final String SELECT_BY_KYC = "SELECT * FROM merchants WHERE kyc_status = :kycStatus";


    public MerchantResponse getById(Long id) {
        return Objects.requireNonNull(namedJdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", id), merchantRowMapper()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Merchant not found with id: " + id));
    }

    public Page<MerchantResponse> findByStatus(MerchantStatus status, int page, int size, String sortField, Sort.Direction sortDirection) {
        MapSqlParameterSource params = new MapSqlParameterSource("status", status.name());
        return queryPage(SELECT_BY_STATUS, params, page, size, sortField, sortDirection);
    }

    public Page<MerchantResponse> findByKycStatus(KycStatus kycStatus, int page, int size, String sortField, Sort.Direction sortDirection) {
        MapSqlParameterSource params = new MapSqlParameterSource("kycStatus", kycStatus.name());
        return queryPage(SELECT_BY_KYC, params, page, size, sortField, sortDirection);
    }

    private Page<MerchantResponse> queryPage(String baseSql, MapSqlParameterSource params, int page, int size, String sortField, Sort.Direction sortDirection) {
        int offset = (page - 1) * size;
        String paginatedSql = String.format("%s ORDER BY %s %s LIMIT %d OFFSET %d",
                baseSql, sortField, sortDirection.name(), size, offset);

        List<MerchantResponse> content = namedJdbc.query(paginatedSql, params, merchantRowMapper());

        String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS total_count";
        Long total = namedJdbc.queryForObject(countSql, params, Long.class);

        return new PageImpl<>(content, PageRequest.of(page - 1, size), total != null ? total : 0);
    }

    public Long insert(MapSqlParameterSource params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(INSERT_MERCHANT, params, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<MerchantResponse> findById(Long id) {
        return namedJdbc.query(SELECT_BY_ID, new MapSqlParameterSource("id", id), merchantRowMapper())
                .stream().findFirst();
    }

    public MerchantValidationResult validateMerchantCreation(Long userId, String tier) {
        return namedJdbc.queryForObject(CREATE_MERCHANT_VALIDATION,
                new MapSqlParameterSource().addValue("userId", userId).addValue("tier", tier),
                (rs, _) -> new MerchantValidationResult(
                        rs.getBoolean("merchant_exists"),
                        rs.getBoolean("user_exists"),
                        rs.getBoolean("tier_exists")
                ));
    }

    public List<MerchantResponse> findWithPage(String baseQuery, MapSqlParameterSource params, String sort, String dir, long[] total) {
        String fullQuery = String.format(baseQuery, sort, dir);
        return namedJdbc.query(fullQuery, params, (rs, rowNum) -> {
            total[0] = rs.getLong("total_count");
            return merchantRowMapper().mapRow(rs, rowNum);
        });
    }

    public Optional<Long> findRoleIdByName(String name) {
        return namedJdbc.query("SELECT id FROM roles WHERE name = :name",
                new MapSqlParameterSource("name", name),
                (rs, _) -> rs.getLong("id")).stream().findFirst();
    }

    public boolean tierExists(String tier) {
        Integer count = namedJdbc.queryForObject("SELECT COUNT(*) FROM tier_configs WHERE tier = :tier",
                new MapSqlParameterSource("tier", tier), Integer.class);
        return count != null && count > 0;
    }

    public boolean exists(Long id) {
        Integer count = namedJdbc.queryForObject("SELECT COUNT(*) FROM merchants WHERE id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id), Integer.class);
        return count != null && count > 0;
    }

    public void updateAddress(Long id, String address, Long updatedBy) {
        namedJdbc.update("UPDATE merchants SET address = :address, updated_at = now(), updated_by = :updatedBy WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("address", address).addValue("updatedBy", updatedBy));
    }

    public void updateKycStatus(Long id, String kycStatus, Long updatedBy) {
        namedJdbc.update(UPDATE_KYC_STATUS, new MapSqlParameterSource().addValue("id", id)
                .addValue("kycStatus", kycStatus).addValue("updatedBy", updatedBy));
    }

    public void updateMerchantStatus(Long id, String merchantStatus, Long updatedBy) {
        namedJdbc.update(UPDATE_MERCHANT_STATUS, new MapSqlParameterSource().addValue("id", id)
                .addValue("merchantStatus", merchantStatus).addValue("updatedBy", updatedBy));
    }

    public void updateMerchantStatusAndKycStatus(Long id, String merchantStatus, String kycStatus, Long updatedBy) {
        namedJdbc.update(UPDATE_MERCHANT_STATUS_AND_KYC, new MapSqlParameterSource().addValue("id", id)
                .addValue("merchantStatus", merchantStatus).addValue("kycStatus", kycStatus).addValue("updatedBy", updatedBy));
    }

    public void updateTier(Long id, String tier, Long updatedBy) {
        namedJdbc.update("UPDATE merchants SET tier = :tier, updated_at = now(), updated_by = :updatedBy WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("tier", tier).addValue("updatedBy", updatedBy));
    }

    public void softDelete(Long id, Long deletedBy) {
        namedJdbc.update("UPDATE merchants SET deleted_at = now(), deleted_by = :deletedBy WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("deletedBy", deletedBy));
    }

    public String getSelectAllSql() { return String.format(SELECT_BASE, "1=1", "%s", "%s"); }

    private RowMapper<MerchantResponse> merchantRowMapper() {
        return (rs, _) -> new MerchantResponse(
                rs.getLong("id"),
                rs.getString("address"),
                KycStatus.valueOf(rs.getString("kyc_status")),
                MerchantStatus.valueOf(rs.getString("merchant_status")),
                MerchantTier.valueOf(rs.getString("tier")),
                rs.getLong("user_id"),
                rs.getString("firstname"),
                rs.getString("lastname"),
                rs.getString("email"),
                rs.getString("phone"),
                UserStatus.valueOf(rs.getString("user_status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}