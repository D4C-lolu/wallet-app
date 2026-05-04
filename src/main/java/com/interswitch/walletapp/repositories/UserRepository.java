package com.interswitch.walletapp.repositories;

import com.interswitch.walletapp.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u JOIN FETCH u.role r LEFT JOIN FETCH r.permissions WHERE u.email = :email")
    Optional<User> findByEmail(String email);

    @Query(value = "SELECT sp_user_update_role(:id, :roleId)", nativeQuery = true)
    void updateRole(@Param("id") Long id, @Param("roleId") Long roleId);
}
