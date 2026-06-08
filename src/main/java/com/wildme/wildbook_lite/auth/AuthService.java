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
import com.wildme.wildbook_lite.auth.dto.RegisterRequest;
import com.wildme.wildbook_lite.exception.BusinessException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered");
        }
        User user = new User(
            req.username(),
            req.email(),
            passwordEncoder.encode(req.password())
        );
        userRepository.save(user);
        return issueToken(user.getUsername());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
            return issueToken(auth.getName());
        } catch (BadCredentialsException ex) {
            throw new BusinessException("Invalid username or password");
        }
    }

    private AuthResponse issueToken(String username) {
        JwtService.TokenPair pair = jwtService.issue(username);
        return new AuthResponse(pair.token(), username, pair.expiresAt());
    }
}
