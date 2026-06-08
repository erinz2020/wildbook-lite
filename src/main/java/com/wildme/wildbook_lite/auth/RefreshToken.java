package com.wildme.wildbook_lite.auth;

import java.time.Instant;

import com.wildme.wildbook_lite.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Server-side handle for a refresh token.
 *
 * Why store this in the DB at all (the access token doesn't need a row):
 *  - Access tokens are short-lived (60min). When they expire, the client
 *    swaps a long-lived refresh token for a new access token.
 *  - Storing refresh tokens server-side gives us a *revocation list*: log
 *    out a user → delete the row → all their future refreshes fail.
 *  - We store only the SHA-256 hash, not the raw token. Even a DB leak
 *    can't be replayed against /api/auth/refresh.
 */
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
    indexes = @Index(name = "ix_refresh_user_revoked", columnList = "user_id, revoked")
)
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex of the raw token. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public RefreshToken() {}

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
