package dev.trailhead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration class that binds values from application.properties into Java objects,
 * instead of scattering @Value("${auth.refresh-token.cookie-name}") annotations throughout your services.
 */
@ConfigurationProperties(prefix = "auth.refresh-token")
public record RefreshTokenProperties(
        long expirationMs,
        String delivery,
        String cookieName,
        boolean cookieSecure,
        String cookieSameSite,
        String cookiePath,
        long cookieMaxAgeSeconds
) {

    public boolean isCookieDelivery() {
        return "cookie".equalsIgnoreCase(delivery);
    }
}
