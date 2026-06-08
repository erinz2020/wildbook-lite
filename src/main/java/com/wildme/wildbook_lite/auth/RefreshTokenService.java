package com.wildme.wildbook_lite.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.config.AppProperties;
import com.wildme.wildbook_lite.exception.BusinessException;

@Service
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int RAW_BYTES = 48; // 64-char base64url after encoding

    private final RefreshTokenRepository repo;
    private final long lifetimeDays;

    public RefreshTokenService(RefreshTokenRepository repo, AppProperties props) {
        this.repo = repo;
        this.lifetimeDays = props.jwt().refreshLifetimeDays();
    }

    /** Returns the RAW token to give the client. The DB only stores the hash. */
    @Transactional
    public Issued issue(Long userId) {
        String raw = randomToken();
        Instant expiresAt = Instant.now().plus(lifetimeDays, ChronoUnit.DAYS);
        repo.save(new RefreshToken(userId, sha256(raw), expiresAt));
        return new Issued(raw, expiresAt);
    }

    /**
     * Rotates: validates the presented raw token, revokes it, mints a new
     * one. Rotation makes refresh tokens single-use → if one is stolen,
     * the legitimate user's next refresh invalidates the attacker's copy.
     */
    @Transactional
    public Rotated rotate(String rawToken) {
        RefreshToken existing = repo.findByTokenHash(sha256(rawToken))
            .orElseThrow(() -> new BusinessException("Invalid refresh token"));
        if (existing.isRevoked() || existing.isExpired()) {
            throw new BusinessException("Refresh token revoked or expired");
        }
        existing.setRevoked(true);
        repo.save(existing);

        Issued next = issue(existing.getUserId());
        return new Rotated(existing.getUserId(), next.token(), next.expiresAt());
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return repo.revokeAllForUser(userId);
    }

    public record Issued(String token, Instant expiresAt) {}
    public record Rotated(Long userId, String token, Instant expiresAt) {}

    private static String randomToken() {
        byte[] buf = new byte[RAW_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
