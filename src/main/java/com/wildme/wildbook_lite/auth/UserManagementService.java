package com.wildme.wildbook_lite.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.dto.UpdateProfileRequest;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserManagementService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User me() {
        return getById(SecurityUtils.currentUserId());
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User updateMe(UpdateProfileRequest req) {
        User me = me();

        // Email change: enforce uniqueness explicitly to give a friendlier error
        if (req.email() != null && !req.email().equals(me.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new BusinessException("Email already registered");
            }
            me.setEmail(req.email());
        }

        // Password change: require the OLD password — defence-in-depth in
        // case an attacker steals an access token but doesn't know the password
        if (req.newPassword() != null) {
            if (req.currentPassword() == null
                || !passwordEncoder.matches(req.currentPassword(), me.getPasswordHash())) {
                throw new BusinessException("Current password is incorrect");
            }
            me.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            // Invalidate all refresh tokens on password change
            refreshTokenService.revokeAllForUser(me.getId());
        }
        return userRepository.save(me);
    }
}
