package com.wildme.wildbook_lite.auth.dto;

import java.time.Instant;

public record AuthResponse(
    String token,
    String username,
    Instant expiresAt
) {}
