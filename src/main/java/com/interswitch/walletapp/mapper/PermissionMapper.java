package com.interswitch.walletapp.mapper;

import com.interswitch.walletapp.entities.Permission;
import com.interswitch.walletapp.models.response.PermissionResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    PermissionResponse map(Permission permission);

    List<PermissionResponse> map(List<Permission> permissions);
}