package com.platform.user.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A platform user (customer). Owns its own invariants; persistence details arrive in Phase 3. */
public class User {

    private final UUID id;
    private final String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(UUID id, String email, String firstName, String lastName, String phoneNumber, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.email = normalizeEmail(email);
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public void updateProfile(String firstName, String lastName, String phoneNumber, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.updatedAt = now;
    }

    public void changeStatus(UserStatus newStatus, Instant now) {
        this.status = Objects.requireNonNull(newStatus);
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
