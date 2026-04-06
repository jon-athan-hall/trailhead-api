package dev.trailhead.auth;

import dev.trailhead.config.VerificationProperties;
import dev.trailhead.exception.TokenRefreshException;
import dev.trailhead.token.EmailVerificationToken;
import dev.trailhead.token.EmailVerificationTokenRepository;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final VerificationProperties verificationProperties;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    JavaMailSender mailSender,
                                    VerificationProperties verificationProperties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.verificationProperties = verificationProperties;
    }

    public void sendVerificationEmail(User user) {
        String tokenValue = UUID.randomUUID().toString();

        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenValue);
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusMillis(verificationProperties.expirationMs()));
        tokenRepository.save(token);

        String verificationLink = verificationProperties.frontendBaseUrl()
                + "/verify?token=" + tokenValue;

        // Log to console for local development when no mail host is configured.
        if (mailHost == null || mailHost.isBlank()) {
            log.info("===== VERIFICATION EMAIL =====");
            log.info("To: {}", user.getEmail());
            log.info("Subject: Verify your email");
            log.info("Link: {}", verificationLink);
            log.info("==============================");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Verify your email");
        message.setText("Click the link below to verify your email address:\n\n"
                + verificationLink + "\n\n"
                + "This link expires in 24 hours.");

        mailSender.send(message);
    }

    @Transactional
    public void verify(String tokenValue) {
        EmailVerificationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new TokenRefreshException("Invalid verification token"));

        if (token.getConfirmedAt() != null) {
            throw new TokenRefreshException("Email already verified");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenRefreshException("Verification token has expired");
        }

        token.setConfirmedAt(Instant.now());
        tokenRepository.save(token);

        User user = token.getUser();
        user.setVerified(true);
        userRepository.save(user);
    }
}
