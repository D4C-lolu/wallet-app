package com.interswitch.walletapp.dao;

import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserDao {

    private final NamedParameterJdbcTemplate namedJdbc;

    public Map<String, Object> validateForCreate(String email, String phone, Long roleId) {
        return namedJdbc.queryForMap(
                "SELECT * FROM sp_user_validate_for_create(:email, :phone, :roleId)",
                new MapSqlParameterSource()
                        .addValue("email", email)
                        .addValue("phone", phone)
                        .addValue("roleId", roleId)
        );
    }

    public Map<String, Object> validateForUpdate(Long id, String email, String phone) {
        return namedJdbc.queryForMap(
                "SELECT * FROM sp_user_validate_for_update(:id, :email, :phone)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("email", email)
                        .addValue("phone", phone)
        );
    }

    public Long insert(MapSqlParameterSource params) {
        return namedJdbc.queryForObject(
                "SELECT sp_user_insert(:firstname, :lastname, :othername, :email, :phone, :passwordHash, :userStatus, :roleId, :createdBy, :updatedBy)",
                params, Long.class
        );
    }

    public Optional<UserResponse> findById(Long id) {
        return namedJdbc.query(
                "SELECT * FROM sp_user_find_by_id(:id)",
                new MapSqlParameterSource("id", id),
                userRowMapper()
        ).stream().findFirst();
    }

    public List<UserResponse> findAll(int limit, int offset, String sortField, String direction, long[] total) {
        return namedJdbc.query(
                "SELECT * FROM sp_user_find_all(:limit, :offset, :sortField, :sortDirection)",
                new MapSqlParameterSource()
                        .addValue("limit", limit)
                        .addValue("offset", offset)
                        .addValue("sortField", sortField)
                        .addValue("sortDirection", direction),
                (rs, rowNum) -> {
                    total[0] = rs.getLong("total_count");
                    return userRowMapper().mapRow(rs, rowNum);
                });
    }

    public void update(MapSqlParameterSource params) {
        namedJdbc.queryForList(
                "SELECT sp_user_update(:id, :firstname, :lastname, :othername, :email, :phone)",
                params
        );
    }

    public void updateStatus(Long id, String userStatus) {
        namedJdbc.queryForList(
                "SELECT sp_user_update_status(:id, :userStatus)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userStatus", userStatus)
        );
    }

    public void updateRole(Long id, Long roleId) {
        namedJdbc.queryForList(
                "SELECT sp_user_update_role(:id, :roleId)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("roleId", roleId)
        );
    }

    public void updatePassword(Long id, String passwordHash) {
        namedJdbc.queryForList(
                "SELECT sp_user_update_password(:id, :passwordHash)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("passwordHash", passwordHash)
        );
    }

    public void softDelete(Long id, Long deletedBy) {
        namedJdbc.queryForList(
                "SELECT sp_user_soft_delete(:id, :deletedBy)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("deletedBy", deletedBy)
        );
    }

    public Optional<String> findRoleNameById(Long roleId) {
        String result = namedJdbc.queryForObject(
                "SELECT sp_user_find_role_name_by_id(:roleId)",
                new MapSqlParameterSource("roleId", roleId),
                String.class
        );
        return Optional.ofNullable(result);
    }

    public String findPasswordHashById(Long id) {
        return namedJdbc.queryForObject(
                "SELECT sp_user_find_password_hash_by_id(:id)",
                new MapSqlParameterSource("id", id),
                String.class
        );
    }

    public boolean existsById(Long id) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject(
                "SELECT sp_user_exists_by_id(:id)",
                new MapSqlParameterSource("id", id),
                Boolean.class
        ));
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
