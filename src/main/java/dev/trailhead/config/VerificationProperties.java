package dev.trailhead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.verification")
public record VerificationProperties(
        long expirationMs,
        String frontendBaseUrl
) {
}
