package com.wildme.wildbook_lite.auth.dto;

import java.time.Instant;

public record AuthResponse(
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    String username
) {}
