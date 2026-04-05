package dev.trailhead.token;

import dev.trailhead.config.RefreshTokenProperties;
import dev.trailhead.exception.TokenRefreshException;
import dev.trailhead.user.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional annotation used throughout to ensure everything is rolled back if one step fails.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties refreshTokenProperties;

    // Spring automatically injects when there's only one constructor.
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               RefreshTokenProperties refreshTokenProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    // Build a new token with a random UUID, sets the expiration, and saves.
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenProperties.expirationMs()));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    // When a user refreshes their access token, verify the old refresh is valid, revoke that old token, create new.
    @Transactional
    public RefreshToken rotateRefreshToken(String tokenValue) {
        RefreshToken existing = verify(tokenValue);
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return createRefreshToken(existing.getUser());
    }

    public RefreshToken verify(String tokenValue) {
        // Check if the token exists.
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        // Check if the token has been revoked.
        if (refreshToken.isRevoked()) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }

        // Check if the token has expired.
        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenRefreshException("Refresh token has expired");
        }

        return refreshToken;
    }

    // Used by logout. No need to throw error, just let the logout happen.
    @Transactional
    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    // Useful for logging out of all devices.
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredOrRevoked();
    }
}
