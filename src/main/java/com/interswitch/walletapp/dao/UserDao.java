package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String CREATE_USER_VALIDATION = """
            SELECT
                (SELECT COUNT(*) FROM users WHERE email = :email AND deleted_at IS NULL) AS email_exists,
                (SELECT COUNT(*) FROM users WHERE phone = :phone AND deleted_at IS NULL) AS phone_exists,
                (SELECT name FROM roles WHERE id = :roleId) AS role_name
            """;

    private static final String UPDATE_USER_VALIDATION = """
            SELECT
                (SELECT COUNT(*) FROM users WHERE email = :email AND deleted_at IS NULL AND id != :id) AS email_exists,
                (SELECT COUNT(*) FROM users WHERE phone = :phone AND deleted_at IS NULL AND id != :id) AS phone_exists
            """;

    private static final String INSERT_USER = """
            INSERT INTO users (firstname, lastname, othername, email, phone, password_hash,
                user_status, role_id, created_by, updated_by, created_at, updated_at)
            VALUES (:firstname, :lastname, :othername, :email, :phone, :passwordHash,
                :userStatus, :roleId, :createdBy, :updatedBy, now(), now())
            """;

    private static final String SELECT_BY_ID = """
            SELECT u.*, r.name AS role_name
            FROM users u
            JOIN roles r ON r.id = u.role_id
            WHERE u.id = :id AND u.deleted_at IS NULL
            """;

    private static final String SELECT_ALL = """
            SELECT u.*, r.name AS role_name, COUNT(*) OVER() AS total_count
            FROM users u
            JOIN roles r ON r.id = u.role_id
            WHERE u.deleted_at IS NULL
            ORDER BY %s %s
            LIMIT :limit OFFSET :offset
            """;

    private static final String UPDATE_USER = """
            UPDATE users SET firstname = :firstname, lastname = :lastname, othername = :othername,
                email = :email, phone = :phone, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String UPDATE_STATUS = """
            UPDATE users SET user_status = :userStatus, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String UPDATE_ROLE = """
            UPDATE users SET role_id = :roleId, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String UPDATE_PASSWORD = """
            UPDATE users SET password_hash = :passwordHash, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String SOFT_DELETE = """
            UPDATE users SET deleted_at = now(), deleted_by = :deletedBy, updated_at = now()
            WHERE id = :id AND deleted_at IS NULL
            """;

    private static final String ROLE_NAME_BY_ID = """
            SELECT name FROM roles WHERE id = :roleId
            """;

    private static final String PASSWORD_BY_ID = """
            SELECT password_hash FROM users WHERE id = :id AND deleted_at IS NULL
            """;

    public Map<String, Object> validateForCreate(String email, String phone, Long roleId) {
        return namedJdbc.queryForMap(
                CREATE_USER_VALIDATION,
                new MapSqlParameterSource()
                        .addValue("email", email)
                        .addValue("phone", phone)
                        .addValue("roleId", roleId)
        );
    }

    public Map<String, Object> validateForUpdate(Long id, String email, String phone) {
        return namedJdbc.queryForMap(
                UPDATE_USER_VALIDATION,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("email", email)
                        .addValue("phone", phone)
        );
    }

    public Long insert(MapSqlParameterSource params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(INSERT_USER, params, keyHolder, new String[]{"id"});
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<UserResponse> findById(Long id) {
        return namedJdbc.query(
                SELECT_BY_ID,
                new MapSqlParameterSource("id", id),
                userRowMapper()
        ).stream().findFirst();
    }

    public List<UserResponse> findAll(int limit, int offset, String sortField, String direction, long[] total) {
        String query = SELECT_ALL.formatted(sortField, direction);
        return namedJdbc.query(query,
                new MapSqlParameterSource()
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return userRowMapper().mapRow(rs, rowNum);
                });
    }

    public void update(MapSqlParameterSource params) {
        namedJdbc.update(UPDATE_USER, params);
    }

    public void updateStatus(Long id, String userStatus) {
        namedJdbc.update(UPDATE_STATUS,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userStatus", userStatus));
    }

    public void updateRole(Long id, Long roleId) {
        namedJdbc.update(UPDATE_ROLE,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("roleId", roleId));
    }

    public void updatePassword(Long id, String passwordHash) {
        namedJdbc.update(UPDATE_PASSWORD,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("passwordHash", passwordHash));
    }

    public void softDelete(Long id, Long deletedBy) {
        namedJdbc.update(SOFT_DELETE,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("deletedBy", deletedBy));
    }

    public Optional<String> findRoleNameById(Long roleId) {
        return namedJdbc.query(
                ROLE_NAME_BY_ID,
                new MapSqlParameterSource("roleId", roleId),
                (rs, _) -> rs.getString("name")
        ).stream().findFirst();
    }

    public String findPasswordHashById(Long id) {
        return namedJdbc.queryForObject(
                PASSWORD_BY_ID,
                new MapSqlParameterSource("id", id),
                String.class
        );
    }

    public boolean existsById(Long id) {
        Integer count = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", id),
                Integer.class
        );
        return count != null && count > 0;
    }

    private RowMapper<UserResponse> userRowMapper() {
        return (rs, rowNum) -> new UserResponse(
                rs.getLong("id"),
                rs.getString("firstname"),
                rs.getString("lastname"),
                rs.getString("othername"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("role_name"),
                UserStatus.valueOf(rs.getString("user_status")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}