package com.platform.user.service;

import com.platform.common.web.error.ConflictException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.user.api.dto.CreateUserRequest;
import com.platform.user.api.dto.UpdateUserRequest;
import com.platform.user.api.dto.UserValidationResponse;
import com.platform.user.domain.User;
import com.platform.user.domain.UserStatus;
import com.platform.user.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository repository;
    private final Clock clock;

    public UserService(UserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public User create(CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        // Phase 3 adds a unique index on email, which also closes the check-then-insert race.
        if (repository.existsByEmail(email)) {
            throw new ConflictException("USER_EMAIL_ALREADY_EXISTS", "A user with this email already exists");
        }
        User user = new User(UUID.randomUUID(), email, request.firstName(), request.lastName(),
                request.phoneNumber(), clock.instant());
        return repository.save(user);
    }

    public User get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public Page<User> search(UserStatus status, String email, Pageable pageable) {
        return repository.search(status, email, pageable);
    }

    public User update(UUID id, UpdateUserRequest request) {
        User user = get(id);
        user.updateProfile(request.firstName(), request.lastName(), request.phoneNumber(), clock.instant());
        return repository.save(user);
    }

    public User changeStatus(UUID id, UserStatus status) {
        User user = get(id);
        user.changeStatus(status, clock.instant());
        return repository.save(user);
    }

    public void delete(UUID id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("User", id);
        }
    }

    public UserValidationResponse validateForOrdering(UUID id) {
        User user = get(id);
        return new UserValidationResponse(user.getId(), user.getStatus(), user.getStatus().canPlaceOrders());
    }
}
