package dev.trailhead.user.dto;

import jakarta.validation.constraints.NotNull;

public record AddRoleRequest(
        @NotNull(message = "Role ID is required")
        Long roleId
) {
}
