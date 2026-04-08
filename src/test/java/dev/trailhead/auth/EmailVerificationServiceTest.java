package dev.trailhead.auth;

import dev.trailhead.config.VerificationProperties;
import dev.trailhead.exception.TokenRefreshException;
import dev.trailhead.token.EmailVerificationToken;
import dev.trailhead.token.EmailVerificationTokenRepository;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        VerificationProperties properties = new VerificationProperties(86400000, "http://localhost:3000");
        // @InjectMocks doesn't pick up record-only constructor args mixed with field-injected @Value, so set manually.
        emailVerificationService = new EmailVerificationService(
                tokenRepository, userRepository, mailSender, properties);

        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setVerified(false);
    }

    @Test
    void sendVerificationEmail_whenMailHostBlank_shouldSaveTokenAndSkipSend() {
        ReflectionTestUtils.setField(emailVerificationService, "mailHost", "");

        emailVerificationService.sendVerificationEmail(testUser);

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();

        assertNotNull(saved.getToken());
        assertEquals(testUser, saved.getUser());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendVerificationEmail_whenMailHostConfigured_shouldSendMail() {
        ReflectionTestUtils.setField(emailVerificationService, "mailHost", "smtp.example.com");

        emailVerificationService.sendVerificationEmail(testUser);

        verify(tokenRepository).save(any(EmailVerificationToken.class));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage sent = messageCaptor.getValue();

        assertNotNull(sent.getTo());
        assertEquals("test@example.com", sent.getTo()[0]);
        assertEquals("Verify your email", sent.getSubject());
        assertTrue(sent.getText().contains("http://localhost:3000/verify?token="));
    }

    @Test
    void verify_validToken_shouldMarkUserVerified() {
        EmailVerificationToken token = validToken();
        when(tokenRepository.findByToken("good-token")).thenReturn(Optional.of(token));

        emailVerificationService.verify("good-token");

        assertNotNull(token.getConfirmedAt());
        assertTrue(testUser.isVerified());
        verify(tokenRepository).save(token);
        verify(userRepository).save(testUser);
    }

    @Test
    void verify_invalidToken_shouldThrow() {
        when(tokenRepository.findByToken("nonexistent")).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class, () -> emailVerificationService.verify("nonexistent"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_expiredToken_shouldThrow() {
        EmailVerificationToken token = validToken();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        assertThrows(TokenRefreshException.class, () -> emailVerificationService.verify("expired"));
        assertFalse(testUser.isVerified());
    }

    @Test
    void verify_alreadyConfirmed_shouldThrow() {
        EmailVerificationToken token = validToken();
        token.setConfirmedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByToken("used")).thenReturn(Optional.of(token));

        assertThrows(TokenRefreshException.class, () -> emailVerificationService.verify("used"));
        verify(userRepository, never()).save(any());
    }

    private EmailVerificationToken validToken() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setId(1L);
        token.setToken("good-token");
        token.setUser(testUser);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
