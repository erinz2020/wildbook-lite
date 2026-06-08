package com.wildme.wildbook_lite.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.wildme.wildbook_lite.config.AppProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Stateless JWT generator + parser.
 *
 *  - HMAC-SHA256 with a 256-bit shared secret (`app.jwt.secret`)
 *  - subject = username
 *  - expires in `app.jwt.expiration-minutes`
 *
 * Interview points:
 *  - 3 parts of a JWT: header.payload.signature, base64url-encoded
 *  - Why HS256 vs RS256: HS256 needs only one secret (server-side);
 *    RS256 enables third parties to verify without holding the secret
 *  - JWT vs Session: stateless (no central store) vs lookup per request
 *  - Why short expiry + refresh token: limit blast radius of leaked token
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        byte[] bytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        // AppProperties already validates length >= 32 at bind time, but we
        // defend in depth in case someone constructs the service manually.
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be >= 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMinutes = props.jwt().expirationMinutes();
    }

    public TokenPair issue(String username) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact();
        return new TokenPair(token, exp);
    }

    /**
     * @return the subject (username) if valid; throws otherwise
     */
    public String parseUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.getSubject();
        } catch (JwtException ex) {
            throw new InvalidJwtException("Invalid or expired token", ex);
        }
    }

    public record TokenPair(String token, Instant expiresAt) {}

    public static class InvalidJwtException extends RuntimeException {
        public InvalidJwtException(String msg, Throwable cause) { super(msg, cause); }
    }
}
