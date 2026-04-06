package dev.trailhead.auth;

import dev.trailhead.auth.dto.*;
import dev.trailhead.config.RefreshTokenProperties;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supports two refresh token delivery modes, configured via auth.refresh-token.delivery:
 * - "cookie": refresh token is sent as a secure HTTP-only cookie (production)
 * - "body": refresh token is included in the JSON response (Postman/testing)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final UserRepository userRepository;
    private final RefreshTokenProperties refreshTokenProperties;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          UserRepository userRepository,
                          RefreshTokenProperties refreshTokenProperties) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.userRepository = userRepository;
        this.refreshTokenProperties = refreshTokenProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        return buildAuthResponse(authResponse, response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        return buildAuthResponse(authResponse, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(request, httpRequest);
        AuthResponse authResponse = authService.refresh(refreshToken);
        return buildAuthResponse(authResponse, httpResponse);
    }

    @PostMapping("/verify")
    public ResponseEntity<MessageResponse> verify(@Valid @RequestBody VerifyRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (user.isVerified()) {
            return ResponseEntity.ok(new MessageResponse("Email is already verified"));
        }

        emailVerificationService.sendVerificationEmail(user);
        return ResponseEntity.ok(new MessageResponse("Verification email sent"));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestBody(required = false) RefreshRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(request, httpRequest);
        authService.logout(refreshToken);

        if (refreshTokenProperties.isCookieDelivery()) {
            clearRefreshTokenCookie(httpResponse);
        }

        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    private String extractRefreshToken(RefreshRequest request, HttpServletRequest httpRequest) {
        if (refreshTokenProperties.isCookieDelivery()) {
            return readCookie(httpRequest, refreshTokenProperties.cookieName());
        }
        return request != null ? request.refreshToken() : null;
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private ResponseEntity<AuthResponse> buildAuthResponse(AuthResponse authResponse,
                                                           HttpServletResponse response) {
        if (refreshTokenProperties.isCookieDelivery()) {
            setRefreshTokenCookie(response, authResponse.refreshToken());
            AuthResponse bodyWithoutRefresh = new AuthResponse(
                    authResponse.accessToken(),
                    null,
                    authResponse.expiresIn()
            );
            return ResponseEntity.ok(bodyWithoutRefresh);
        }
        return ResponseEntity.ok(authResponse);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String tokenValue) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.cookieName(), tokenValue)
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path(refreshTokenProperties.cookiePath())
                .maxAge(refreshTokenProperties.cookieMaxAgeSeconds())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenProperties.cookieName(), "")
                .httpOnly(true)
                .secure(refreshTokenProperties.cookieSecure())
                .sameSite(refreshTokenProperties.cookieSameSite())
                .path(refreshTokenProperties.cookiePath())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
