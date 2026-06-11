package com.wildme.wildbook_lite.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.wildme.wildbook_lite.auth.dto.AuthResponse;
import com.wildme.wildbook_lite.auth.dto.LoginRequest;
import com.wildme.wildbook_lite.exception.BusinessException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Tests for AuthService — login/refresh/logout only. User creation
 * lives in UserManagementService now and is tested separately.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuthenticationManager authManager;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;

    private final AuthMetrics metrics = new AuthMetrics(new SimpleMeterRegistry());

    AuthService svc() {
        return new AuthService(userRepository, authManager,
                               jwtService, refreshTokenService, metrics);
    }

    @Test
    @DisplayName("login on valid creds returns both tokens")
    void login_happyPath() {
        User user = new User("alice", "alice@x.com", "HASH");
        user.setId(1L);

        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "password");
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.issue("alice"))
            .thenReturn(new JwtService.TokenPair("ACC", Instant.now().plusSeconds(60)));
        when(refreshTokenService.issue(1L))
            .thenReturn(new RefreshTokenService.Issued("REF", Instant.now().plusSeconds(86400)));

        AuthResponse resp = svc().login(new LoginRequest("alice", "password"));

        assertThat(resp.accessToken()).isEqualTo("ACC");
        assertThat(resp.refreshToken()).isEqualTo("REF");
        assertThat(resp.username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("login wraps BadCredentialsException into BusinessException (no info leak)")
    void login_badCredentials() {
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenThrow(new BadCredentialsException("ignored internal text"));

        assertThatThrownBy(() -> svc().login(new LoginRequest("alice", "wrong")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid username or password");

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
