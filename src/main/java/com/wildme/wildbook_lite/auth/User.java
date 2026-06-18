package com.wildme.wildbook_lite.auth;

import java.util.EnumSet;
import java.util.Set;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
public class User extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    /** BCrypt hash. Never the raw password. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Whether this user wants to receive transactional email
     * (encounter assignments, publish notifications, etc.). Defaults
     * to true on user-create — admins can toggle via PATCH /api/users/{id}.
     *
     * Why on User and not a separate `notification_preferences` table:
     *  - One toggle, no per-channel granularity needed today.
     *  - Adds one nullable column on a small table; easy to evolve
     *    into a preferences sub-resource later if we grow more flags.
     */
    @Column(name = "email_opt_in", nullable = false)
    private boolean emailOptIn = true;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = Role.class)
    @CollectionTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role"})
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Set<Role> roles = EnumSet.of(Role.RESEARCHER);

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEmailOptIn() { return emailOptIn; }
    public void setEmailOptIn(boolean emailOptIn) { this.emailOptIn = emailOptIn; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
