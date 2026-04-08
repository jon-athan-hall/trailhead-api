package dev.trailhead.token;

import dev.trailhead.config.RefreshTokenProperties;
import dev.trailhead.exception.TokenRefreshException;
import dev.trailhead.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        RefreshTokenProperties properties = new RefreshTokenProperties(
                604800000, "body", "refresh_token", false, "Strict", "/api/auth", 604800
        );
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);

        testUser = new User();
        testUser.setId("11111111-1111-1111-1111-111111111111");
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
    }

    @Test
    void createRefreshToken_shouldSaveAndReturn() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken token = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(token.getToken());
        assertEquals(testUser, token.getUser());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiresAt().isAfter(Instant.now()));

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void verify_withValidToken_shouldReturn() {
        RefreshToken token = validToken();
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.verify("valid-token");

        assertEquals(token, result);
    }

    @Test
    void verify_withRevokedToken_shouldThrow() {
        RefreshToken token = validToken();
        token.setRevoked(true);
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

        assertThrows(TokenRefreshException.class,
                () -> refreshTokenService.verify("revoked-token"));
    }

    @Test
    void verify_withExpiredToken_shouldThrow() {
        RefreshToken token = validToken();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThrows(TokenRefreshException.class,
                () -> refreshTokenService.verify("expired-token"));
    }

    @Test
    void verify_withInvalidToken_shouldThrow() {
        when(refreshTokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class,
                () -> refreshTokenService.verify("nonexistent"));
    }

    @Test
    void rotateRefreshToken_shouldRevokeOldAndCreateNew() {
        RefreshToken existing = validToken();
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken newToken = refreshTokenService.rotateRefreshToken("old-token");

        // Old token should be revoked
        assertTrue(existing.isRevoked());
        // New token should be different
        assertNotEquals("old-token", newToken.getToken());
        assertFalse(newToken.isRevoked());

        // save called twice: once for revoking old, once for creating new
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void revoke_shouldMarkTokenRevoked() {
        RefreshToken token = validToken();
        when(refreshTokenRepository.findByToken("token-to-revoke")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.revoke("token-to-revoke");

        assertTrue(token.isRevoked());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeAllForUser_shouldCallRepository() {
        refreshTokenService.revokeAllForUser("11111111-1111-1111-1111-111111111111");

        verify(refreshTokenRepository).revokeAllByUserId("11111111-1111-1111-1111-111111111111");
    }

    private RefreshToken validToken() {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setToken("old-token");
        token.setUser(testUser);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setRevoked(false);
        return token;
    }
}
