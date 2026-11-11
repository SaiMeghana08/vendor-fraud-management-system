package com.vendorfraud.user.dto;

import java.time.Instant;
import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user,
        List<String> roles) {
}
