package com.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.platform.common.web.error.ConflictException;
import com.platform.common.web.error.ResourceNotFoundException;
import com.platform.user.api.dto.CreateUserRequest;
import com.platform.user.domain.User;
import com.platform.user.domain.UserStatus;
import com.platform.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private UserRepository repository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesEmailAndActivatesUser() {
        given(repository.existsByEmail("ada@example.com")).willReturn(false);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        User user = service.create(new CreateUserRequest("  Ada@Example.COM ", "Ada", "Lovelace", null));

        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsDuplicateEmail() {
        given(repository.existsByEmail("ada@example.com")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("ada@example.com", "Ada", "Lovelace", null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void suspendedUsersAreNotEligibleForOrders() {
        User user = new User(UUID.randomUUID(), "a@b.io", "A", "B", null, NOW);
        user.changeStatus(UserStatus.SUSPENDED, NOW);
        given(repository.findById(user.getId())).willReturn(Optional.of(user));

        assertThat(service.validateForOrdering(user.getId()).eligibleForOrders()).isFalse();
    }

    @Test
    void deletingUnknownUserIsNotFound() {
        UUID id = UUID.randomUUID();
        given(repository.deleteById(id)).willReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
