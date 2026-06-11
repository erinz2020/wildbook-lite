package com.wildme.wildbook_lite.auth;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.auth.dto.AdminCreateUserRequest;
import com.wildme.wildbook_lite.auth.dto.AdminUpdateUserRequest;
import com.wildme.wildbook_lite.auth.dto.UpdateProfileRequest;
import com.wildme.wildbook_lite.auth.dto.UserProfileResponse;
import com.wildme.wildbook_lite.common.PageResponse;

import jakarta.validation.Valid;

/**
 * User management endpoints.
 *
 * Authorization model:
 *
 *   /me ............... any authenticated user (self-scope)
 *   /{id}  GET ........ any authenticated user (public profile view)
 *   /     (collection) . ADMIN
 *   /{id} write ....... ADMIN
 *
 * @PreAuthorize is enforced by Spring Security at method-entry, BEFORE
 * the controller body runs — so even if the service forgot to re-check
 * roles internally, an unauthorised request never reaches it. Defense
 * in depth: the URL says who can hit it, the service also re-checks
 * for self-target rules (e.g., can't delete yourself).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userService;

    public UserController(UserManagementService userService) {
        this.userService = userService;
    }

    // -------- self --------

    @GetMapping("/me")
    public UserProfileResponse me() {
        return UserProfileResponse.from(userService.me());
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return UserProfileResponse.from(userService.updateMe(req));
    }

    // -------- read others (any user) --------

    @GetMapping("/{id}")
    public UserProfileResponse get(@PathVariable Long id) {
        return UserProfileResponse.publicView(userService.getById(id));
    }

    // -------- ADMIN: list / create / update / delete --------

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserProfileResponse> list(
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.from(userService.list(pageable), UserProfileResponse::from);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse createUser(@Valid @RequestBody AdminCreateUserRequest req) {
        return UserProfileResponse.from(userService.createUserByAdmin(req));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse updateUser(@PathVariable Long id,
                                          @Valid @RequestBody AdminUpdateUserRequest req) {
        return UserProfileResponse.from(userService.updateById(id, req));
    }

    /**
     * Soft delete: flips enabled=false and revokes the user's refresh
     * tokens. We deliberately do NOT remove the row — historical
     * references in audit_log / encounters / comments stay resolvable.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
