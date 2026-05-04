package com.interswitch.walletapp.services;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.entities.Role;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.BadRequestException;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.enums.UserStatus;
import com.interswitch.walletapp.models.request.ChangePasswordRequest;
import com.interswitch.walletapp.models.request.CreateUserRequest;
import com.interswitch.walletapp.models.request.UpdateUserRequest;
import com.interswitch.walletapp.models.response.UserResponse;
import com.interswitch.walletapp.repositories.RoleRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("User Service Integration Tests")
public class UserServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupSecurityContext() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com")
                .orElseThrow();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(superAdmin), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should create user successfully")
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                "john.doe@test.com", "55555555555",
                "Admin123!", 2L
        );

        UserResponse response = userService.createUser(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.firstname()).isEqualTo(request.firstname());
        assertThat(response.lastname()).isEqualTo(request.lastname());
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.phone()).isEqualTo(request.phone());
        assertThat(response.userStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("should populate createdBy when creating user")
    void shouldPopulateCreatedByWhenCreatingUser() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();

        CreateUserRequest request = new CreateUserRequest(
                "Jane", "Doe", null,
                "jane.doe@test.com", "66666666666",
                "Admin123!", 2L
        );

        UserResponse response = userService.createUser(request);
        User saved = userRepository.findById(response.id()).orElseThrow();

        assertThat(saved.getCreatedBy()).isEqualTo(superAdmin.getId());
    }

    @Test
    @DisplayName("should fail create user with duplicate email")
    void shouldFailCreateUserWithDuplicateEmail() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();

        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                existing.getEmail(), "77777777777",
                "Admin123!", 2L
        );

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already in use");
    }

    @Test
    @DisplayName("should fail create user with duplicate phone")
    void shouldFailCreateUserWithDuplicatePhone() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();

        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                "unique@test.com", existing.getPhone(),
                "Admin123!", 2L
        );

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Phone already in use");
    }

    @Test
    @DisplayName("should fail create user with non existent role")
    void shouldFailCreateUserWithNonExistentRole() {
        CreateUserRequest request = new CreateUserRequest(
                "John", "Doe", null,
                "unique2@test.com", "99999999999",
                "Admin123!", 9999L
        );

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Role not found");
    }


    @Test
    @DisplayName("should get user by id successfully")
    void shouldGetUserByIdSuccessfully() {
        User existing = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();

        UserResponse response = userService.getUserById(existing.getId());

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.email()).isEqualTo(existing.getEmail());
        assertThat(response.firstname()).isEqualTo(existing.getFirstname());
        assertThat(response.lastname()).isEqualTo(existing.getLastname());
    }

    @Test
    @DisplayName("should fail get user with non existent id")
    void shouldFailGetUserWithNonExistentId() {
        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("should get all users paginated")
    void shouldGetAllUsersPaginated() {
        Page<UserResponse> page = userService.getAllUsers(1, 10, "created_at", Sort.Direction.DESC);

        assertThat(page).isNotNull();
        assertFalse(page.getContent().isEmpty());
        assertThat(page.getContent().size()).isLessThanOrEqualTo(10);
    }


    @Test
    @DisplayName("should update user successfully")
    void shouldUpdateUserSuccessfully() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        UpdateUserRequest request = new UpdateUserRequest(
                "Updated", "Name", null, existing.getPhone(), existing.getEmail()
        );

        UserResponse response = userService.updateUser(existing.getId(), request);

        assertThat(response.firstname()).isEqualTo(request.firstname());
        assertThat(response.lastname()).isEqualTo(request.lastname());
    }

    @Test
    @DisplayName("should fail update user with duplicate email")
    void shouldFailUpdateUserWithDuplicateEmail() {
        User existing    = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        User anotherUser = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();

        UpdateUserRequest request = new UpdateUserRequest(
                existing.getFirstname(), existing.getLastname(), existing.getOthername(), existing.getPhone(), anotherUser.getEmail()
        );

        assertThatThrownBy(() -> userService.updateUser(existing.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    @DisplayName("should fail update user with duplicate phone")
    void shouldFailUpdateUserWithDuplicatePhone() {
        User existing    = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        User anotherUser = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();

        UpdateUserRequest request = new UpdateUserRequest(
                existing.getFirstname(), existing.getLastname(), existing.getOthername(), anotherUser.getPhone(), existing.getEmail()
        );

        assertThatThrownBy(() -> userService.updateUser(existing.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Phone already in use");
    }


    @Test
    @DisplayName("should change user status successfully")
    void shouldChangeUserStatusSuccessfully() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();

        userService.changeUserStatus(existing.getId(), UserStatus.SUSPENDED);

        forceFlush();
        User updated = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getUserStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("should change user role successfully")
    void shouldChangeUserRoleSuccessfully() {
        User existing    = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        Role merchantRole = roleRepository.findByName(Roles.USER).orElseThrow();

        userService.changeUserRole(existing.getId(), merchantRole.getId());

        String roleName = jdbcTemplate.queryForObject(
                "SELECT sp_user_find_role_name_by_id(?)", String.class, existing.getId()
        );
        assertThat(roleName).isEqualTo(merchantRole.getName());
    }

    @Test
    @DisplayName("should fail change user role with non existent role")
    void shouldFailChangeUserRoleWithNonExistentRole() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();

        forceFlush();
        assertThatThrownBy(() -> userService.changeUserRole(existing.getId(), 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Role not found");
    }


    @Test
    @DisplayName("should change password successfully")
    void shouldChangePasswordSuccessfully() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "Admin123!", "NewPassword123!", "NewPassword123!"
        );

        assertThatNoException().isThrownBy(() ->
                userService.changePassword(existing.getId(), request));
    }

    @Test
    @DisplayName("should fail change password with wrong current password")
    void shouldFailChangePasswordWithWrongCurrentPassword() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongPassword!", "NewPassword123!", "NewPassword123!"
        );

        assertThatThrownBy(() -> userService.changePassword(existing.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current password is incorrect");
    }

    @Test
    @DisplayName("should fail change password when passwords do not match")
    void shouldFailChangePasswordWhenPasswordsDoNotMatch() {
        User existing = userRepository.findByEmail("testadmin@verveguard.com").orElseThrow();
        ChangePasswordRequest request = new ChangePasswordRequest(
                "Admin123!", "NewPassword123!", "DifferentPassword123!"
        );

        assertThatThrownBy(() -> userService.changePassword(existing.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    @DisplayName("should soft delete user successfully")
    void shouldSoftDeleteUserSuccessfully() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
        User toDelete   = userRepository.findByEmail("testmerchant@verveguard.com").orElseThrow();

        userService.deleteUser(toDelete.getId());
        forceFlush();
        User deleted = userRepository.findById(toDelete.getId()).orElseThrow();
        assertThat(deleted.isNotDeleted()).isFalse();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getDeletedBy()).isEqualTo(superAdmin.getId());
    }

    @Test
    @DisplayName("should not find soft deleted user")
    void shouldNotFindSoftDeletedUser() {
        User toDelete   = userRepository.findByEmail("testmerchant@verveguard.com").orElseThrow();

        userService.deleteUser(toDelete.getId());

        assertThatThrownBy(() -> userService.getUserById(toDelete.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }
}