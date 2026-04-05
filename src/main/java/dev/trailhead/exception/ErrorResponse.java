package dev.trailhead.exception;

import java.time.Instant;

// Simple data carrier that defines the shape of every error response.
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
