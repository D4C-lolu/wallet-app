package com.interswitch.walletapp.services;

import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.exceptions.ConflictException;
import com.interswitch.walletapp.exceptions.NotFoundException;
import com.interswitch.walletapp.mapper.PermissionMapper;
import com.interswitch.walletapp.models.request.CreatePermissionRequest;
import com.interswitch.walletapp.models.response.PermissionResponse;
import com.interswitch.walletapp.repositories.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByName(request.name())) {
            throw new ConflictException("Permission already exists");
        }

        Permission permission = Permission.builder()
                .name(request.name())
                .description(request.description())
                .build();

        return permissionMapper.map(permissionRepository.save(permission));
    }

    public PermissionResponse getPermissionById(Long permissionId) {
        return permissionMapper.map(permissionRepository.findById(permissionId)
                .orElseThrow(() -> new NotFoundException("Permission not found")));
    }

    public List<PermissionResponse> getAllPermissions() {
        return permissionMapper.map(permissionRepository.findAll());
    }
}