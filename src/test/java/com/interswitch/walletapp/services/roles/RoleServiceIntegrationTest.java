package com.interswitch.walletapp.services.roles;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.constants.Roles;
import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.entities.Role;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.request.CreateRoleRequest;
import com.interswitch.walletapp.models.response.RoleResponse;
import com.interswitch.walletapp.repositories.PermissionRepository;
import com.interswitch.walletapp.repositories.RolePermissionRepository;
import com.interswitch.walletapp.repositories.RoleRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import com.interswitch.walletapp.services.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Role Service Integration Tests")
public class RoleServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @BeforeEach
    void setupSecurityContext() {
        User superAdmin = userRepository.findByEmail("superadmin@verveguard.com").orElseThrow();
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
    @DisplayName("should create role successfully")
    void shouldCreateRoleSuccessfully() {
        CreateRoleRequest request = new CreateRoleRequest("NEW_ROLE");

        RoleResponse response = roleService.createRole(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo(request.name());
    }

    @Test
    @DisplayName("should fail create role with duplicate name")
    void shouldFailCreateRoleWithDuplicateName() {
        Role existing = roleRepository.findByName("ADMIN").orElseThrow();
        CreateRoleRequest request = new CreateRoleRequest(existing.getName());

        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Role already exists");
    }

    @Test
    @DisplayName("should get role by id successfully")
    void shouldGetRoleByIdSuccessfully() {
        Role existing = roleRepository.findByName("ADMIN").orElseThrow();

        RoleResponse response = roleService.getRoleById(existing.getId());

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.name()).isEqualTo(existing.getName());
    }

    @Test
    @DisplayName("should fail get role with non existent id")
    void shouldFailGetRoleWithNonExistentId() {
        assertThatThrownBy(() -> roleService.getRoleById(9999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Role not found");
    }

    @Test
    @DisplayName("should get all roles paginated")
    void shouldGetAllRolesPaginated() {
        Page<RoleResponse> page = roleService.getAllRoles(1, 10, "name", Sort.Direction.ASC);

        assertThat(page).isNotNull();
        assertFalse(page.getContent().isEmpty());
        assertThat(page.getContent().size()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("should assign permission to role successfully")
    void shouldAssignPermissionToRoleSuccessfully() {
        Role role = roleRepository.findByName(Roles.USER).orElseThrow();
        Permission permission = permissionRepository.findByName("tier:read").orElseThrow();

        assertThatNoException().isThrownBy(() ->
                roleService.assignPermission(role.getId(), permission.getId()));

        assertThat(rolePermissionRepository
                .existsByRoleIdAndPermissionId(role.getId(), permission.getId())).isTrue();
    }

    @Test
    @DisplayName("should fail assign permission that is already assigned")
    void shouldFailAssignPermissionAlreadyAssigned() {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        Permission permission = permissionRepository.findByName("user:read").orElseThrow();

        assertThatThrownBy(() -> roleService.assignPermission(role.getId(), permission.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Permission already assigned to role");
    }

    @Test
    @DisplayName("should fail assign permission with non existent role")
    void shouldFailAssignPermissionWithNonExistentRole() {
        Permission permission = permissionRepository.findByName("user:read").orElseThrow();

        assertThatThrownBy(() -> roleService.assignPermission(999L, permission.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Role not found");
    }

    @Test
    @DisplayName("should fail assign permission with non existent permission")
    void shouldFailAssignPermissionWithNonExistentPermission() {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();

        assertThatThrownBy(() -> roleService.assignPermission(role.getId(), 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Permission not found");
    }

    @Test
    @DisplayName("should assign permissions in bulk successfully")
    void shouldAssignPermissionsInBulkSuccessfully() {
        Role role = roleRepository.findByName(Roles.USER).orElseThrow();
        List<Long> permissionIds = permissionRepository.findAll().stream()
                .filter(p -> !rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), p.getId()))
                .limit(3)
                .map(Permission::getId)
                .toList();

        assertThatNoException().isThrownBy(() ->
                roleService.assignPermissions(role.getId(), permissionIds));

        permissionIds.forEach(permissionId ->
                assertThat(rolePermissionRepository
                        .existsByRoleIdAndPermissionId(role.getId(), permissionId)).isTrue());
    }

    @Test
    @DisplayName("should revoke permission from role successfully")
    void shouldRevokePermissionFromRoleSuccessfully() {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        Permission permission = permissionRepository.findByName("user:read").orElseThrow();

        assertThatNoException().isThrownBy(() ->
                roleService.revokePermission(role.getId(), permission.getId()));

        assertThat(rolePermissionRepository
                .existsByRoleIdAndPermissionId(role.getId(), permission.getId())).isFalse();
    }

    @Test
    @DisplayName("should fail revoke permission that is not assigned")
    void shouldFailRevokePermissionNotAssigned() {
        Role role = roleRepository.findByName(Roles.USER).orElseThrow();
        Permission permission = permissionRepository.findByName("role:create").orElseThrow();

        assertThatThrownBy(() -> roleService.revokePermission(role.getId(), permission.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Permission not assigned to role");
    }

    @Test
    @DisplayName("should revoke permissions in bulk successfully")
    void shouldRevokePermissionsInBulkSuccessfully() {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        List<Long> permissionIds = rolePermissionRepository.findByRoleId(role.getId())
                .stream()
                .limit(3)
                .map(rp -> rp.getId().getPermissionId())
                .toList();

        assertThatNoException().isThrownBy(() ->
                roleService.revokePermissions(role.getId(), permissionIds));

        permissionIds.forEach(permissionId ->
                assertThat(rolePermissionRepository
                        .existsByRoleIdAndPermissionId(role.getId(), permissionId)).isFalse());
    }
}