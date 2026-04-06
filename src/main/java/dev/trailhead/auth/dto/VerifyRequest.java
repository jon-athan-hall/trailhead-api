package dev.trailhead.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
