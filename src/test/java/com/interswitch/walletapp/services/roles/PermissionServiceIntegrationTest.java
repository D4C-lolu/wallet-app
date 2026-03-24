package com.interswitch.walletapp.services.roles;

import com.interswitch.walletapp.base.BaseIntegrationTest;
import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.entities.User;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.models.request.CreatePermissionRequest;
import com.interswitch.walletapp.models.response.PermissionResponse;
import com.interswitch.walletapp.repositories.PermissionRepository;
import com.interswitch.walletapp.repositories.UserRepository;
import com.interswitch.walletapp.security.UserPrincipal;
import com.interswitch.walletapp.services.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;


@DisplayName("Permission Service Integration Tests")
public class PermissionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("should create permission successfully")
    void shouldCreatePermissionSuccessfully() {
        CreatePermissionRequest request = new CreatePermissionRequest("new:permission", "A new permission");

        PermissionResponse response = permissionService.createPermission(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.description()).isEqualTo(request.description());
    }

    @Test
    @DisplayName("should fail create permission with duplicate name")
    void shouldFailCreatePermissionWithDuplicateName() {
        Permission existing = permissionRepository.findByName("user:read").orElseThrow();
        CreatePermissionRequest request = new CreatePermissionRequest(existing.getName(), "duplicate");

        assertThatThrownBy(() -> permissionService.createPermission(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Permission already exists");
    }

    @Test
    @DisplayName("should get permission by id successfully")
    void shouldGetPermissionByIdSuccessfully() {
        Permission existing = permissionRepository.findByName("user:read").orElseThrow();

        PermissionResponse response = permissionService.getPermissionById(existing.getId());

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.name()).isEqualTo(existing.getName());
        assertThat(response.description()).isEqualTo(existing.getDescription());
    }

    @Test
    @DisplayName("should fail get permission with non existent id")
    void shouldFailGetPermissionWithNonExistentId() {
        assertThatThrownBy(() -> permissionService.getPermissionById(999999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Permission not found");
    }

    @Test
    @DisplayName("should get all permissions successfully")
    void shouldGetAllPermissionsSuccessfully() {
        List<PermissionResponse> permissions = permissionService.getAllPermissions();

        assertFalse(permissions.isEmpty());
        assertThat(permissions.size()).isGreaterThanOrEqualTo(1);
    }
}