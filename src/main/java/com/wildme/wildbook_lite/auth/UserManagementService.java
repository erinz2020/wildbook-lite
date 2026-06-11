package com.wildme.wildbook_lite.auth;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.dto.AdminCreateUserRequest;
import com.wildme.wildbook_lite.auth.dto.AdminUpdateUserRequest;
import com.wildme.wildbook_lite.auth.dto.UpdateProfileRequest;
import com.wildme.wildbook_lite.common.Audited;
import com.wildme.wildbook_lite.exception.BusinessException;
import com.wildme.wildbook_lite.exception.NotFoundException;

/**
 * Owns every user mutation. Two distinct surfaces share this service:
 *
 *   - SELF surface   : updateMe()       — the user updates their own profile,
 *                                          requires current password to change pwd
 *   - ADMIN surface  : createUserByAdmin / updateById / deleteById
 *                                        — an admin acts on someone else
 *
 * Keeping both in one Service is fine BECAUSE every method's
 * authorization is enforced upstream (UserController has @PreAuthorize
 * on each admin endpoint, /me endpoints are inherently self-scoped).
 * If we wanted to split SELF and ADMIN into separate services to
 * harden against caller mistakes, we could — but the line is currently
 * clean.
 */
@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthMetrics metrics;

    public UserManagementService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService,
                                 AuthMetrics metrics) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.metrics = metrics;
    }

    // ============================================================
    // Read
    // ============================================================

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

    // ============================================================
    // SELF surface — caller is acting on their OWN account
    // ============================================================

    @Transactional
    public User updateMe(UpdateProfileRequest req) {
        User me = me();

        if (req.email() != null && !req.email().equals(me.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new BusinessException("Email already registered");
            }
            me.setEmail(req.email());
        }

        if (req.newPassword() != null) {
            if (req.currentPassword() == null
                || !passwordEncoder.matches(req.currentPassword(), me.getPasswordHash())) {
                throw new BusinessException("Current password is incorrect");
            }
            me.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            // Self password change → revoke all sessions (defence in depth).
            refreshTokenService.revokeAllForUser(me.getId());
        }
        return userRepository.save(me);
    }

    // ============================================================
    // ADMIN surface — caller (admin) is acting on someone ELSE
    // ============================================================

    @Audited("user.create")
    @Transactional
    public User createUserByAdmin(AdminCreateUserRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException("Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered");
        }
        User user = new User(req.username(), req.email(),
                             passwordEncoder.encode(req.password()));

        // Default to RESEARCHER when admin didn't specify any roles.
        // Storing the role default in ONE place (here) avoids drift
        // between the entity default, the seed, and admin creation.
        Set<Role> roles = (req.roles() == null || req.roles().isEmpty())
            ? EnumSet.of(Role.RESEARCHER)
            : EnumSet.copyOf(req.roles());
        user.setRoles(roles);
        User saved = userRepository.save(user);
        metrics.onRegisterSuccess();
        return saved;
    }

    @Audited("user.update")
    @Transactional
    public User updateById(Long id, AdminUpdateUserRequest req) {
        User user = getById(id);

        if (req.email() != null && !req.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new BusinessException("Email already registered");
            }
            user.setEmail(req.email());
        }

        if (req.newPassword() != null) {
            // Admin reset — does NOT need the old password.
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            // Force the affected user to re-login.
            refreshTokenService.revokeAllForUser(id);
        }

        if (req.roles() != null && !req.roles().isEmpty()) {
            user.setRoles(EnumSet.copyOf(req.roles()));
        }

        if (req.enabled() != null) {
            // Disabling via PATCH is allowed (admin discretion). If
            // we're disabling, also revoke the user's refresh tokens so
            // they can't bounce a new access token via a stale refresh.
            if (Boolean.FALSE.equals(req.enabled()) && user.isEnabled()) {
                refreshTokenService.revokeAllForUser(id);
            }
            user.setEnabled(req.enabled());
        }
        return userRepository.save(user);
    }

    /**
     * Soft delete. Why not hard delete:
     *
     *   audit_log.user_id, encounter.submitter_user_id,
     *   comments.author_user_id and friends all reference this user as
     *   a plain Long FK. A hard delete would leave them pointing at a
     *   ghost — historical records that say "this was done by user 42"
     *   would show "unknown user". Bad UX, bad forensics.
     *
     *   Soft delete is the production-correct answer: keep the row,
     *   flip `enabled=false`, revoke tokens so the account can't be
     *   used. Spring Security's UserDetails.isEnabled() picks this up
     *   automatically — failed login.
     *
     *   A separate "hard purge" workflow (GDPR right-to-erasure) would
     *   anonymize the row instead of deleting it.
     */
    @Audited("user.delete")
    @Transactional
    public void deleteById(Long id) {
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BusinessException("Refusing to delete the currently authenticated admin");
        }
        User user = getById(id);
        if (!user.isEnabled()) {
            return; // idempotent
        }
        user.setEnabled(false);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(id);
    }
}
