package dev.trailhead.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Email(message = "Email must be valid")
        String email
) {
}
