package dev.trailhead.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Enables Spring Data JPA Auditing and provides the AuditorAware bean that supplies the
 * currently authenticated user's ID for the @CreatedBy and @LastModifiedBy fields.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }

            // The JWT subject is the user ID set during token generation.
            Object principal = authentication.getPrincipal();
            if (principal instanceof Jwt jwt) {
                return Optional.ofNullable(jwt.getSubject());
            }

            return Optional.empty();
        };
    }
}
