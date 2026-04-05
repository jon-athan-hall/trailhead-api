package dev.trailhead.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Annotation tells Jackson to skip any fields that's null when serializing to JSON.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
