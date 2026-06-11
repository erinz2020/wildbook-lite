package com.wildme.wildbook_lite.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.auth.dto.AuthResponse;
import com.wildme.wildbook_lite.auth.dto.LoginRequest;
import com.wildme.wildbook_lite.auth.dto.RefreshRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // NOTE: public POST /register was removed. New users must be created
    // by an admin via POST /api/users. This is a deliberate policy choice:
    // a research platform should not have an open sign-up surface.

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // Requires a valid Bearer token; revokes ALL refresh tokens for this user.
        authService.logout(SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
