package dev.trailhead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password-reset")
public record PasswordResetProperties(
        long expirationMs,
        String frontendBaseUrl
) {
}
