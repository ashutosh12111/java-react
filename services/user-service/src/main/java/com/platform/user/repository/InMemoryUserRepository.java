package com.platform.user.repository;

import com.platform.common.web.paging.InMemoryPageSupport;
import com.platform.user.domain.User;
import com.platform.user.domain.UserStatus;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryUserRepository implements UserRepository {

    static final Map<String, Comparator<User>> SORTABLE = Map.of(
            "createdAt", Comparator.comparing(User::getCreatedAt),
            "email", Comparator.comparing(User::getEmail),
            "lastName", Comparator.comparing(User::getLastName));

    private final Map<UUID, User> users = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.values().stream().anyMatch(u -> u.getEmail().equals(email));
    }

    @Override
    public Page<User> search(UserStatus status, String email, Pageable pageable) {
        return InMemoryPageSupport.page(
                users.values().stream()
                        .filter(u -> status == null || u.getStatus() == status)
                        .filter(u -> email == null || u.getEmail().equals(User.normalizeEmail(email))),
                pageable, SORTABLE, Comparator.comparing(User::getCreatedAt).thenComparing(User::getId));
    }

    @Override
    public boolean deleteById(UUID id) {
        return users.remove(id) != null;
    }
}
