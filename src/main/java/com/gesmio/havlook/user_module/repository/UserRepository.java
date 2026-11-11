
package com.gesmio.havlook.user_module.repository;

import com.gesmio.havlook.user_module.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find user by email (case-insensitive)
    Optional<User> findByEmailIgnoreCase(String email);

    // Find user by phone number
    Optional<User> findByPhone(String phone);

    // Check whether email already exists
    boolean existsByEmailIgnoreCase(String email);

    // Check whether phone number already exists
    boolean existsByPhone(String phone);
}
