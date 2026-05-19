package com.ec.api.repository;

import com.ec.api.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUserName(String userName);
}
