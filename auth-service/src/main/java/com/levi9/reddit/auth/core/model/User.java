package com.levi9.reddit.auth.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "is_banned", nullable = false)
    private boolean banned;

    @Column(name = "ban_reason", columnDefinition = "text")
    private String banReason;

    @Column(name = "banned_at")
    private Instant bannedAt;

    @Column(name = "banned_by")
    private Long bannedBy;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Logins made before this get no more tokens (see {@code TokenClaims}). Null: never changed. */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    protected User() {}

    public User(String username, String email, String passwordHash, String firstName, String lastName) {
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isBanned() {
        return banned;
    }

    public String getBanReason() {
        return banReason;
    }

    public Instant getBannedAt() {
        return bannedAt;
    }

    public Long getBannedBy() {
        return bannedBy;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateProfile(String firstName, String lastName, String email) {
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
        if (email != null) {
            this.email = email;
        }
    }

    public void changePasswordHash(String passwordHash, Instant changedAt) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.passwordChangedAt = Objects.requireNonNull(changedAt, "changedAt");
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void delete() {
        this.deleted = true;
    }

    public void ban(Long adminId, String reason, Instant at) {
        Instant bannedAt = Objects.requireNonNull(at, "at");
        this.bannedBy = Objects.requireNonNull(adminId, "adminId");
        this.bannedAt = bannedAt;
        this.banReason = reason;
        this.banned = true;
    }

    public void unban() {
        this.banned = false;
        this.bannedBy = null;
        this.bannedAt = null;
        this.banReason = null;
    }
}
