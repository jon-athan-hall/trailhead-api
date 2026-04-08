package dev.trailhead.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.trailhead.auth.dto.ForgotPasswordRequest;
import dev.trailhead.auth.dto.LoginRequest;
import dev.trailhead.auth.dto.RefreshRequest;
import dev.trailhead.auth.dto.RegisterRequest;
import dev.trailhead.auth.dto.ResetPasswordRequest;
import dev.trailhead.auth.dto.VerifyRequest;
import dev.trailhead.token.EmailVerificationToken;
import dev.trailhead.token.EmailVerificationTokenRepository;
import dev.trailhead.token.PasswordResetToken;
import dev.trailhead.token.PasswordResetTokenRepository;
import dev.trailhead.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void register_shouldReturnTokens() throws Exception {
        var request = new RegisterRequest("Test User", "test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    void register_duplicateEmail_shouldReturn409() throws Exception {
        var request = new RegisterRequest("Test User", "dup@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_invalidInput_shouldReturn400() throws Exception {
        var request = new RegisterRequest("", "not-an-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void login_shouldReturnTokens() throws Exception {
        register("login@example.com", "password123");

        var loginRequest = new LoginRequest("login@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    void login_badCredentials_shouldReturn401() throws Exception {
        register("bad@example.com", "password123");

        var loginRequest = new LoginRequest("bad@example.com", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void refresh_shouldReturnNewTokens() throws Exception {
        String refreshToken = registerAndGetRefreshToken("refresh@example.com");

        var refreshRequest = new RefreshRequest(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(not(refreshToken)));
    }

    @Test
    void refresh_withRevokedToken_shouldReturn401() throws Exception {
        String refreshToken = registerAndGetRefreshToken("revoked@example.com");

        // Use refresh token once (rotates it, revoking the old one)
        var refreshRequest = new RefreshRequest(refreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)));

        // Try to use the old (now revoked) token again
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_shouldRevokeRefreshToken() throws Exception {
        String refreshToken = registerAndGetRefreshToken("logout@example.com");

        var logoutRequest = new RefreshRequest(refreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Verify refresh token is no longer valid
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_shouldReturn200() throws Exception {
        String accessToken = registerAndGetAccessToken("protected@example.com");

        mockMvc.perform(get("/actuator/health")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/auth/verify ───────────────────────────────────────────────

    @Test
    void verify_validToken_shouldMarkVerified() throws Exception {
        register("verify@example.com", "password123");
        EmailVerificationToken token = latestVerificationToken();

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRequest(token.getToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));

        assertTrueLater("user should be verified",
                () -> userRepository.findByEmail("verify@example.com").orElseThrow().isVerified());
    }

    @Test
    void verify_invalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRequest("nonexistent"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_expiredToken_shouldReturn401() throws Exception {
        register("expired@example.com", "password123");
        EmailVerificationToken token = latestVerificationToken();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        emailVerificationTokenRepository.save(token);

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRequest(token.getToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_alreadyConfirmed_shouldReturn401() throws Exception {
        register("alreadyverified@example.com", "password123");
        EmailVerificationToken token = latestVerificationToken();

        // First verify - succeeds.
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRequest(token.getToken()))))
                .andExpect(status().isOk());

        // Second verify - rejected.
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRequest(token.getToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_blankToken_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyRequest(""))))
                .andExpect(status().isBadRequest());
    }

    // ── /api/auth/forgot-password ───────────────────────────────────────

    @Test
    void forgotPassword_validEmail_shouldReturnOk() throws Exception {
        register("forgot@example.com", "password123");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("forgot@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset email sent"));
    }

    @Test
    void forgotPassword_unknownEmail_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("nobody@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgotPassword_invalidEmail_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("not-an-email"))))
                .andExpect(status().isBadRequest());
    }

    // ── /api/auth/reset-password ────────────────────────────────────────

    @Test
    void resetPassword_validToken_shouldChangePassword() throws Exception {
        register("reset@example.com", "password123");
        requestPasswordReset("reset@example.com");
        PasswordResetToken token = latestResetToken();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest(token.getToken(), "newPassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));

        // Login with the new password should succeed.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("reset@example.com", "newPassword123"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_invalidToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("nonexistent", "newPassword123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_reusedToken_shouldReturn401() throws Exception {
        register("reused@example.com", "password123");
        requestPasswordReset("reused@example.com");
        PasswordResetToken token = latestResetToken();

        // First use succeeds.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest(token.getToken(), "newPassword123"))))
                .andExpect(status().isOk());

        // Second use rejected.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest(token.getToken(), "anotherPassword123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPassword_shortPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest("any-token", "short"))))
                .andExpect(status().isBadRequest());
    }

    // ── /api/auth/resend-verification ───────────────────────────────────

    @Test
    void resendVerification_authenticatedUnverified_shouldReturnOk() throws Exception {
        String accessToken = registerAndGetAccessToken("resend@example.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent"));
    }

    @Test
    void resendVerification_alreadyVerified_shouldReturnAlreadyVerifiedMessage() throws Exception {
        String accessToken = registerAndGetAccessToken("alreadydone@example.com");

        // Mark verified directly.
        var user = userRepository.findByEmail("alreadydone@example.com").orElseThrow();
        user.setVerified(true);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/resend-verification")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email is already verified"));
    }

    // ── Helper Methods ──────────────────────────────────────────────────

    private EmailVerificationToken latestVerificationToken() {
        List<EmailVerificationToken> all = emailVerificationTokenRepository.findAll();
        return all.get(all.size() - 1);
    }

    private PasswordResetToken latestResetToken() {
        List<PasswordResetToken> all = passwordResetTokenRepository.findAll();
        return all.get(all.size() - 1);
    }

    private void requestPasswordReset(String email) throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(email))));
    }

    private void assertTrueLater(String message, java.util.function.BooleanSupplier condition) {
        if (!condition.getAsBoolean()) {
            throw new AssertionError(message);
        }
    }


    private void register(String email, String password) throws Exception {
        var request = new RegisterRequest("Test User", email, password);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String registerAndGetRefreshToken(String email) throws Exception {
        var request = new RegisterRequest("Test User", email, "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("refreshToken").asText();
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        var request = new RegisterRequest("Test User", email, "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
