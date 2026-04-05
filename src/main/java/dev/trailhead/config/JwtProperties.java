package dev.trailhead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration class that binds values from application.properties into Java objects,
 * instead of scattering @Value("${auth.jwt.expiration-ms}") annotations throughout your services.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String issuer,
        long expirationMs
) {
}
