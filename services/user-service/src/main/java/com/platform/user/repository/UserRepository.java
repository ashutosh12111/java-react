package com.platform.user.repository;

import com.platform.user.domain.User;
import com.platform.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence port for users. Phase 1 uses an in-memory implementation; Phase 3 replaces it with a
 * Spring Data JPA repository over the service's own PostgreSQL database without changing callers.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    boolean existsByEmail(String email);

    Page<User> search(UserStatus status, String email, Pageable pageable);

    boolean deleteById(UUID id);
}
