package dev.trailhead.auth;

import dev.trailhead.config.PasswordResetProperties;
import dev.trailhead.exception.TokenRefreshException;
import dev.trailhead.token.PasswordResetToken;
import dev.trailhead.token.PasswordResetTokenRepository;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    private User testUser;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties(900000, "http://localhost:5173");
        passwordResetService = new PasswordResetService(
                tokenRepository, userRepository, mailSender, passwordEncoder, properties);

        testUser = new User();
        testUser.setId("11111111-1111-1111-1111-111111111111");
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPassword("old-encoded");
    }

    @Test
    void requestReset_userExists_mailHostBlank_shouldSaveTokenAndSkipSend() {
        ReflectionTestUtils.setField(passwordResetService, "mailHost", "");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        passwordResetService.requestReset("test@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertNotNull(saved.getToken());
        assertEquals(testUser, saved.getUser());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));

        verifyNoInteractions(mailSender);
    }

    @Test
    void requestReset_userExists_mailHostConfigured_shouldSendMail() {
        ReflectionTestUtils.setField(passwordResetService, "mailHost", "smtp.example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        passwordResetService.requestReset("test@example.com");

        verify(tokenRepository).save(any(PasswordResetToken.class));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("test@example.com", sent.getTo()[0]);
        assertEquals("Reset your password", sent.getSubject());
        assertTrue(sent.getText().contains("http://localhost:5173/reset-password?token="));
    }

    @Test
    void requestReset_userNotFound_shouldThrow() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> passwordResetService.requestReset("missing@example.com"));
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resetPassword_validToken_shouldUpdatePassword() {
        PasswordResetToken token = validToken();
        when(tokenRepository.findByToken("good-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-encoded");

        passwordResetService.resetPassword("good-token", "newPassword123");

        assertNotNull(token.getUsedAt());
        assertEquals("new-encoded", testUser.getPassword());
        verify(tokenRepository).save(token);
        verify(userRepository).save(testUser);
    }

    @Test
    void resetPassword_invalidToken_shouldThrow() {
        when(tokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class,
                () -> passwordResetService.resetPassword("nonexistent", "newPassword123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_alreadyUsed_shouldThrow() {
        PasswordResetToken token = validToken();
        token.setUsedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByToken("used")).thenReturn(Optional.of(token));

        assertThrows(TokenRefreshException.class,
                () -> passwordResetService.resetPassword("used", "newPassword123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_expiredToken_shouldThrow() {
        PasswordResetToken token = validToken();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        assertThrows(TokenRefreshException.class,
                () -> passwordResetService.resetPassword("expired", "newPassword123"));
        verify(userRepository, never()).save(any());
    }

    private PasswordResetToken validToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(1L);
        token.setToken("good-token");
        token.setUser(testUser);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
