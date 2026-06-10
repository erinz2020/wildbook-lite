package com.wildme.wildbook_lite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wildme.wildbook_lite.auth.dto.AuthResponse;
import com.wildme.wildbook_lite.auth.dto.LoginRequest;
import com.wildme.wildbook_lite.auth.dto.RegisterRequest;
import com.wildme.wildbook_lite.exception.BusinessException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Service-layer unit test with multiple mocked collaborators.
 *
 * Demonstrates:
 *  - ArgumentCaptor → assert on what was passed to a mock
 *  - verify(...) → assert mock interactions
 *  - never(...) → negative assertion (no DB writes on failed login)
 *  - Real AuthMetrics (not mocked) — wrap a real SimpleMeterRegistry
 *    because the SUT only does counter.increment() against it, no need
 *    to mock primitive math.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authManager;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;

    private final AuthMetrics metrics = new AuthMetrics(new SimpleMeterRegistry());

    AuthService svc() {
        return new AuthService(userRepository, passwordEncoder, authManager,
                               jwtService, refreshTokenService, metrics);
    }

    @Test
    @DisplayName("register hashes password, persists user, returns token pair")
    void register_happyPath() {
        RegisterRequest req = new RegisterRequest("alice", "alice@x.com", "password123");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@x.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASH");
        when(jwtService.issue("alice"))
            .thenReturn(new JwtService.TokenPair("ACC", Instant.now().plusSeconds(60)));
        when(refreshTokenService.issue(anyLong()))
            .thenReturn(new RefreshTokenService.Issued("REF", Instant.now().plusSeconds(86400)));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(saved.capture())).thenAnswer(inv -> {
            User u = saved.getValue();
            u.setId(1L);                 // simulate DB-assigned id
            return u;
        });

        AuthResponse resp = svc().register(req);

        // Persistence: password was hashed before save
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASH");
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");

        // Response: both tokens returned
        assertThat(resp.accessToken()).isEqualTo("ACC");
        assertThat(resp.refreshToken()).isEqualTo("REF");
        assertThat(resp.username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("register rejects duplicate username and does NOT touch the DB further")
    void register_duplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> svc().register(new RegisterRequest("alice", "x@x.com", "password123")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Username already taken");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login wraps BadCredentialsException into BusinessException (no info leak)")
    void login_badCredentials() {
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenThrow(new BadCredentialsException("ignored internal text"));

        assertThatThrownBy(() -> svc().login(new LoginRequest("alice", "wrong")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid username or password"); // generic, not the internal text

        verify(refreshTokenService, never()).issue(anyLong());
    }

    @Test
    @DisplayName("logout revokes all refresh tokens for the user")
    void logout_revokesAll() {
        when(refreshTokenService.revokeAllForUser(42L)).thenReturn(3);

        svc().logout(42L);

        verify(refreshTokenService, times(1)).revokeAllForUser(42L);
    }
}
