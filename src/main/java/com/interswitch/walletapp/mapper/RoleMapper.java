package com.interswitch.walletapp.mapper;

import com.interswitch.walletapp.entities.Role;
import com.interswitch.walletapp.models.response.RoleResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    RoleResponse map(Role role);

    List<RoleResponse> map(List<Role> roles);
}

