package com.interswitch.walletapp.services;

import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.entities.Role;
import com.interswitch.walletapp.entities.RolePermission;
import com.interswitch.walletapp.entities.RolePermissionId;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.mapper.RoleMapper;
import com.interswitch.walletapp.models.request.CreateRoleRequest;
import com.interswitch.walletapp.models.response.RoleResponse;
import com.interswitch.walletapp.repositories.PermissionRepository;
import com.interswitch.walletapp.repositories.RolePermissionRepository;
import com.interswitch.walletapp.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleMapper roleMapper;

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("Role already exists");
        }

        Role role = Role.builder()
                .name(request.name())
                .build();

        return roleMapper.map(roleRepository.save(role));
    }

    @Transactional
    public void assignPermission(Long roleId, Long permissionId) {
        if (!roleRepository.existsById(roleId)) {
            throw new NotFoundException("Role not found");
        }
        if (!permissionRepository.existsById(permissionId)) {
            throw new NotFoundException("Permission not found");
        }
        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw new ConflictException("Permission already assigned to role");
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(new RolePermissionId(roleId, permissionId));

        rolePermission.setPermission(new Permission(permissionId));
        rolePermission.setRole(new Role(roleId));

        rolePermissionRepository.save(rolePermission);
    }

    @Transactional
    public void revokePermission(Long roleId, Long permissionId) {
        if (!rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw new NotFoundException("Permission not assigned to role");
        }
        rolePermissionRepository.deleteById(new RolePermissionId(roleId, permissionId));
    }

    public RoleResponse getRoleById(Long roleId) {
        return roleMapper.map(roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role not found")));
    }

    public List<RoleResponse> getAllRoles() {
        return roleMapper.map(roleRepository.findAll());
    }

    public Page<RoleResponse> getAllRoles(int page, int size, String sortField, Sort.Direction sortDirection) {
        Sort sort = Sort.by(sortDirection, sortField);
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        return roleRepository.findAll(pageable)
                .map(roleMapper::map);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {

        if (!roleRepository.existsById(roleId)) {
            throw new NotFoundException("Role not found");
        }

        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new NotFoundException("One or more permissions not found");
        }

        Set<Long> existingPermissionIds =
                rolePermissionRepository.findPermissionIdsByRoleId(roleId);

        List<RolePermission> toAssign = permissions.stream()
                .filter(p -> !existingPermissionIds.contains(p.getId()))
                .map(p -> {
                    RolePermission rp = new RolePermission();
                    rp.setId(new RolePermissionId(roleId, p.getId()));
                    rp.setPermission(new Permission(p.getId()));
                    rp.setRole(new Role(roleId));
                    return rp;
                })
                .toList();

        rolePermissionRepository.saveAll(toAssign);
    }

    @Transactional
    public void revokePermissions(Long roleId, List<Long> permissionIds) {
        if (!roleRepository.existsById(roleId)) {
            throw new NotFoundException("Role not found");
        }

        List<RolePermissionId> toRevoke = permissionIds.stream()
                .map(permissionId -> new RolePermissionId(roleId, permissionId))
                .toList();

        rolePermissionRepository.deleteAllById(toRevoke);
    }
}