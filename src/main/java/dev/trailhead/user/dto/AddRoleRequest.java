package dev.trailhead.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AddRoleRequest(
        @NotBlank(message = "Role ID is required")
        String roleId
) {
}
