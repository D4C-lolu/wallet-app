package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.RolePermission;
import com.interswitch.walletapp.entities.RolePermissionId;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Set;

import static org.hibernate.jpa.HibernateHints.HINT_CACHEABLE;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    @QueryHints(@QueryHint(name = HINT_CACHEABLE, value = "true"))
            boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @QueryHints(@QueryHint(name = HINT_CACHEABLE, value = "true"))
    List<RolePermission> findByRoleId(Long roleId);

    @Query("""
        SELECT rp.id.permissionId
        FROM RolePermission rp
        WHERE rp.id.roleId = :roleId
    """)
            Set<Long> findPermissionIdsByRoleId(Long roleId);
}
