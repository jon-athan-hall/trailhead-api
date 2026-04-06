package dev.trailhead.user.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Record is a compact way to declare a class that's just a data carrier. Comes with private final fields,
 * constructor, getter methods, and other implementations for equals, toString, etc.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        boolean verified,
        Set<String> roles,
        Instant createdAt
) {
}
