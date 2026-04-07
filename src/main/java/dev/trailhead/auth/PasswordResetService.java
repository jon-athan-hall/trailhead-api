package dev.trailhead.auth;

import dev.trailhead.config.PasswordResetProperties;
import dev.trailhead.exception.TokenRefreshException;
import dev.trailhead.token.PasswordResetToken;
import dev.trailhead.token.PasswordResetTokenRepository;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                JavaMailSender mailSender,
                                PasswordEncoder passwordEncoder,
                                PasswordResetProperties properties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public void requestReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("No account found with email: " + email));

        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken token = new PasswordResetToken();
        token.setToken(tokenValue);
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusMillis(properties.expirationMs()));
        tokenRepository.save(token);

        String resetLink = properties.frontendBaseUrl() + "/reset-password?token=" + tokenValue;

        // Log to console for local development when no mail host is configured.
        if (mailHost == null || mailHost.isBlank()) {
            log.info("===== PASSWORD RESET EMAIL =====");
            log.info("To: {}", user.getEmail());
            log.info("Subject: Reset your password");
            log.info("Link: {}", resetLink);
            log.info("================================");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Reset your password");
        message.setText("Click the link below to reset your password:\n\n"
                + resetLink + "\n\n"
                + "This link expires in 15 minutes. If you didn't request this, ignore this email.");

        mailSender.send(message);
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new TokenRefreshException("Invalid reset token"));

        if (token.getUsedAt() != null) {
            throw new TokenRefreshException("Reset token has already been used");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenRefreshException("Reset token has expired");
        }

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
