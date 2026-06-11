package com.wildme.wildbook_lite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wildme.wildbook_lite.config.AppProperties;

/**
 * Pure unit test — no Spring context, no DB.
 *
 *  - Construct the SUT (System Under Test) by hand with normal `new`.
 *  - Test the contract directly. Runs in single-digit milliseconds.
 *
 * Why this style instead of @SpringBootTest:
 *  - 1000x faster than booting Spring.
 *  - No accidental coupling to bean wiring.
 *  - Forces the SUT to have explicit constructor params, which is
 *    already the testable design we want.
 */
class JwtServiceTest {

    private JwtService jwt;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
            new AppProperties.Jwt(
                "test-secret-please-use-at-least-32-bytes-here-aaaaaaaa",
                60,
                30
            ),
            new AppProperties.Storage("./tmp"),
            new AppProperties.Scheduling(false, "0 0 3 * * *", "0 0 4 * * *", 30),
            new AppProperties.OpenSearch(false, "localhost", 9200, "http", "encounters", "", "")
        );
        jwt = new JwtService(props);
    }

    @Test
    @DisplayName("issue then parse round-trips the username")
    void issueRoundtrip() {
        JwtService.TokenPair pair = jwt.issue("alice");

        assertThat(pair.token()).isNotBlank();
        assertThat(pair.expiresAt()).isAfter(Instant.now());
        assertThat(jwt.parseUsername(pair.token())).isEqualTo("alice");
    }

    @Test
    @DisplayName("parseUsername rejects a tampered token")
    void rejectsTampered() {
        JwtService.TokenPair pair = jwt.issue("alice");

        // Flip the last character — breaks the HMAC signature
        char[] chars = pair.token().toCharArray();
        chars[chars.length - 1] = chars[chars.length - 1] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThatThrownBy(() -> jwt.parseUsername(tampered))
            .isInstanceOf(JwtService.InvalidJwtException.class);
    }

    @Test
    @DisplayName("parseUsername rejects garbage input")
    void rejectsGarbage() {
        assertThatThrownBy(() -> jwt.parseUsername("not.a.jwt"))
            .isInstanceOf(JwtService.InvalidJwtException.class);
    }

    @Test
    @DisplayName("constructor rejects a too-short secret (defence in depth)")
    void rejectsShortSecret() {
        AppProperties bad = new AppProperties(
            new AppProperties.Jwt("too-short", 60, 30),
            new AppProperties.Storage("./tmp"),
            new AppProperties.Scheduling(false, "*", "*", 30),
            new AppProperties.OpenSearch(false, "localhost", 9200, "http", "encounters", "", "")
        );
        assertThatThrownBy(() -> new JwtService(bad))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(">= 32 bytes");
    }
}
