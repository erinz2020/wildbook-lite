package com.wildme.wildbook_lite.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.dto.AuthResponse;
import com.wildme.wildbook_lite.auth.dto.LoginRequest;
import com.wildme.wildbook_lite.auth.dto.RefreshRequest;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;

/**
 * Authentication operations: login / refresh / logout.
 *
 * Note that user *creation* lives in UserManagementService now —
 * because:
 *  - public self-registration is removed (admin-only path)
 *  - issuing access tokens (an Auth concern) and creating user rows
 *    (a User-Management concern) are two different jobs; mixing them
 *    pulls Auth into managing role policy, which it shouldn't.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthMetrics metrics;

    public AuthService(UserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AuthMetrics metrics) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.metrics = metrics;
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
            User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new NotFoundException("User vanished: " + auth.getName()));
            metrics.onLoginSuccess();
            return issuePair(user);
        } catch (BadCredentialsException ex) {
            metrics.onLoginFailure();
            throw new BusinessException("Invalid username or password");
        }
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(req.refreshToken());
        User user = userRepository.findById(rotated.userId())
            .orElseThrow(() -> new NotFoundException("User not found: " + rotated.userId()));
        JwtService.TokenPair access = jwtService.issue(user.getUsername());
        return new AuthResponse(
            access.token(), access.expiresAt(),
            rotated.token(), rotated.expiresAt(),
            user.getUsername()
        );
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    private AuthResponse issuePair(User user) {
        JwtService.TokenPair access = jwtService.issue(user.getUsername());
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user.getId());
        return new AuthResponse(
            access.token(), access.expiresAt(),
            refresh.token(), refresh.expiresAt(),
            user.getUsername()
        );
    }
}
