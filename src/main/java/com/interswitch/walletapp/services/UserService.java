package com.interswitch.walletapp.services;

import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.request.ChangePasswordRequest;
import com.interswitch.walletapp.models.request.CreateUserRequest;
import com.interswitch.walletapp.models.request.UpdateUserRequest;
import com.interswitch.walletapp.models.response.UserResponse;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final NamedParameterJdbcTemplate namedJdbc;
    private final PasswordEncoder passwordEncoder;

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
                user_status, role_id, created_at, updated_at)
            VALUES (:firstname, :lastname, :othername, :email, :phone, :passwordHash,
                :userStatus, :roleId, now(), now())
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

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "firstname", "lastname", "email", "phone", "user_status", "created_at", "updated_at"
    );

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Map<String, Object> validation = namedJdbc.queryForMap(
                CREATE_USER_VALIDATION,
                new MapSqlParameterSource()
                        .addValue("email", request.email())
                        .addValue("phone", request.phone())
                        .addValue("roleId", request.roleId())
        );

        if (((Number) validation.get("email_exists")).intValue() > 0) {
            throw new ConflictException("Email already in use");
        }
        if (((Number) validation.get("phone_exists")).intValue() > 0) {
            throw new ConflictException("Phone already in use");
        }

        String roleName = (String) validation.get("role_name");
        if (roleName == null) {
            throw new NotFoundException("Role not found");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("firstname", request.firstname())
                .addValue("lastname", request.lastname())
                .addValue("othername", request.othername())
                .addValue("email", request.email())
                .addValue("phone", request.phone())
                .addValue("passwordHash", passwordEncoder.encode(request.password()))
                .addValue("userStatus", UserStatus.ACTIVE.name())
                .addValue("roleId", request.roleId());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(INSERT_USER, params, keyHolder, new String[]{"id"});
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();

        return new UserResponse(
                id,
                request.firstname(),
                request.lastname(),
                request.othername(),
                request.email(),
                request.phone(),
                roleName,
                UserStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserResponse existing = getUserById(userId);

        Map<String, Object> validation = namedJdbc.queryForMap(
                UPDATE_USER_VALIDATION,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("email", request.email())
                        .addValue("phone", request.phone())
        );

        Map<String, String> conflicts = new LinkedHashMap<>();
        if (((Number) validation.get("email_exists")).intValue() > 0) {
            conflicts.put("email", "Email already in use");
        }
        if (((Number) validation.get("phone_exists")).intValue() > 0) {
            conflicts.put("phone", "Phone already in use");
        }
        if (!conflicts.isEmpty()) {
            throw new ConflictException(String.join(", ", conflicts.values()));
        }

        namedJdbc.update(UPDATE_USER,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("firstname", request.firstname())
                        .addValue("lastname", request.lastname())
                        .addValue("othername", request.othername())
                        .addValue("email", request.email())
                        .addValue("phone", request.phone()));

        return new UserResponse(
                existing.id(),
                request.firstname(),
                request.lastname(),
                request.othername(),
                request.email(),
                request.phone(),
                existing.roleName(),
                existing.userStatus(),
                existing.createdAt(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public UserResponse changeUserStatus(Long userId, UserStatus status) {
        UserResponse existing = getUserById(userId);

        namedJdbc.update(UPDATE_STATUS,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("userStatus", status.name()));

        return new UserResponse(
                existing.id(),
                existing.firstname(),
                existing.lastname(),
                existing.othername(),
                existing.email(),
                existing.phone(),
                existing.roleName(),
                status,
                existing.createdAt(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public UserResponse changeUserRole(Long userId, Long roleId) {
        UserResponse existing = getUserById(userId);

        String roleName = namedJdbc.queryForObject(
                ROLE_NAME_BY_ID,
                new MapSqlParameterSource("roleId", roleId),
                String.class
        );
        if (roleName == null) {
            throw new NotFoundException("Role not found");
        }

        namedJdbc.update(UPDATE_ROLE,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("roleId", roleId));

        return new UserResponse(
                existing.id(),
                existing.firstname(),
                existing.lastname(),
                existing.othername(),
                existing.email(),
                existing.phone(),
                roleName,
                existing.userStatus(),
                existing.createdAt(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public void deleteUser(Long userId) {
        if (!userExists(userId)) {
            throw new NotFoundException("User not found");
        }

        Long deletedBy = SecurityUtil.findCurrentUserId().orElse(null);
        namedJdbc.update(SOFT_DELETE,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("deletedBy", deletedBy));
    }

    @Transactional
    public void deleteCurrentUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        deleteUser(userId);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        getUserById(userId);

        String currentHash = namedJdbc.queryForObject(
                PASSWORD_BY_ID,
                new MapSqlParameterSource("id", userId),
                String.class
        );

        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new BadRequestException("Current password is incorrect");
        }

        namedJdbc.update(UPDATE_PASSWORD,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("passwordHash", passwordEncoder.encode(request.newPassword())));
    }

    public UserResponse getUserById(Long userId) {
        return namedJdbc.query(
                SELECT_BY_ID,
                new MapSqlParameterSource("id", userId),
                userRowMapper()
        ).stream().findFirst().orElseThrow(() -> new NotFoundException("User not found"));
    }

    public Page<UserResponse> getAllUsers(int page, int size, String sortField, Sort.Direction direction) {
        String query = SELECT_ALL.formatted(validateSortField(sortField), direction.name());
        int offset = (page - 1) * size;
        long[] total = {0};

        List<UserResponse> users = namedJdbc.query(query,
                new MapSqlParameterSource()
                        .addValue("limit", size)
                        .addValue("offset", offset),
                (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return userRowMapper().mapRow(rs, rowNum);
                });

        return new PageImpl<>(users, PageRequest.of(page - 1, size), total[0]);
    }

    private boolean userExists(Long userId) {
        Integer count = namedJdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = :id AND deleted_at IS NULL",
                new MapSqlParameterSource("id", userId),
                Integer.class
        );
        return count != null && count > 0;
    }

    private String validateSortField(String sortField) {
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException("Invalid sort field: " + sortField);
        }
        return sortField;
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