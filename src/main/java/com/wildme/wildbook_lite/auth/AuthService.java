package com.wildme.wildbook_lite.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.dto.AuthResponse;
import com.wildme.wildbook_lite.auth.dto.LoginRequest;
import com.wildme.wildbook_lite.auth.dto.RefreshRequest;
import com.wildme.wildbook_lite.auth.dto.RegisterRequest;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered");
        }
        User user = new User(req.username(), req.email(), passwordEncoder.encode(req.password()));
        userRepository.save(user);
        return issuePair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
            User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new NotFoundException("User vanished: " + auth.getName()));
            return issuePair(user);
        } catch (BadCredentialsException ex) {
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
