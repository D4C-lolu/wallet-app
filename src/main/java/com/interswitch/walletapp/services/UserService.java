package com.interswitch.walletapp.services;

import com.interswitch.walletapp.dao.UserDao;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.request.ChangePasswordRequest;
import com.interswitch.walletapp.models.request.CreateUserRequest;
import com.interswitch.walletapp.models.request.UpdateUserRequest;
import com.interswitch.walletapp.models.response.UserResponse;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "firstname", "lastname", "email", "phone", "user_status", "created_at", "updated_at"
    );

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        Map<String, Object> validation = userDao.validateForCreate(
                request.email(), request.phone(), request.roleId()
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

        Long userId =  SecurityUtil.findCurrentUserId().orElse(null);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("firstname", request.firstname())
                .addValue("lastname", request.lastname())
                .addValue("othername", request.othername())
                .addValue("email", request.email())
                .addValue("phone", request.phone())
                .addValue("passwordHash", passwordEncoder.encode(request.password()))
                .addValue("userStatus", UserStatus.ACTIVE.name())
                .addValue("roleId", request.roleId())
                .addValue("createdBy", userId)
                .addValue("updatedBy", userId);

        Long id = userDao.insert(params);

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

        Map<String, Object> validation = userDao.validateForUpdate(
                userId, request.email(), request.phone()
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

        userDao.update(new MapSqlParameterSource()
                .addValue("id", userId)
                .addValue("firstname", request.firstname())
                .addValue("lastname", request.lastname())
                .addValue("othername", request.othername())
                .addValue("email", request.email())
                .addValue("phone", request.phone())
                .addValue("updatedBy", SecurityUtil.getCurrentUserId()));

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
        userDao.updateStatus(userId, status.name());

        // Invalidate tokens when user is suspended/deactivated
        if (status == UserStatus.SUSPENDED || status == UserStatus.INACTIVE) {
            tokenService.revokeAll(userId);
        }

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

        String roleName = userDao.findRoleNameById(roleId)
                .orElseThrow(()-> new NotFoundException("Role not found") );

        userRepository.updateRole(userId, roleId);

        // Invalidate tokens since permissions are embedded in JWT
        tokenService.revokeAll(userId);

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

        if (!userDao.existsById(userId)) {
            throw new NotFoundException("User not found");
        }

        Long deletedBy = SecurityUtil.findCurrentUserId().orElse(null);
        userDao.softDelete(userId, deletedBy);
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

        String currentHash = userDao.findPasswordHashById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new BadRequestException("Current password is incorrect");
        }

        userDao.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
    }

    public UserResponse getUserById(Long userId) {
        return userDao.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public Page<UserResponse> getAllUsers(int page, int size, String sortField, Sort.Direction direction) {
        String validatedSort = validateSortField(sortField);
        int offset = (page - 1) * size;
        long[] total = {0};

        List<UserResponse> users = userDao.findAll(size, offset, validatedSort, direction.name(), total);

        return new PageImpl<>(users, PageRequest.of(page - 1, size), total[0]);
    }

    private String validateSortField(String sortField) {
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException("Invalid sort field: " + sortField);
        }
        return sortField;
    }
}