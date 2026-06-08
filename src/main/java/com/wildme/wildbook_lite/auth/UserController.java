package com.wildme.wildbook_lite.auth;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.auth.dto.UpdateProfileRequest;
import com.wildme.wildbook_lite.auth.dto.UserProfileResponse;
import com.wildme.wildbook_lite.common.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userService;

    public UserController(UserManagementService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse me() {
        return UserProfileResponse.from(userService.me());
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        return UserProfileResponse.from(userService.updateMe(req));
    }

    /** Anyone authenticated can look up a user's public profile. */
    @GetMapping("/{id}")
    public UserProfileResponse get(@PathVariable Long id) {
        return UserProfileResponse.publicView(userService.getById(id));
    }

    /** Admin-only: list every user. Demonstrates role-based @PreAuthorize. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserProfileResponse> list(
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.from(userService.list(pageable), UserProfileResponse::from);
    }
}
