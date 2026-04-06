package dev.trailhead.auth;

import dev.trailhead.auth.dto.AuthResponse;
import dev.trailhead.auth.dto.LoginRequest;
import dev.trailhead.auth.dto.RegisterRequest;
import dev.trailhead.exception.EmailAlreadyExistsException;
import dev.trailhead.role.Role;
import dev.trailhead.role.RoleRepository;
import dev.trailhead.token.RefreshToken;
import dev.trailhead.token.RefreshTokenService;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration layer for all things authentication
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
    }

    // Rollback the entire method if a step fail, like token creation for example.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email is already taken.
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        // Create a new User and hash the password.
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        // Add the basic role to this user.
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Default role ROLE_USER not found"));
        user.getRoles().add(userRole);

        // Save the user.
        user = userRepository.save(user);

        // Generate both tokens and return them.
        String accessToken = jwtTokenService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Send verification email to the new user.
        emailVerificationService.sendVerificationEmail(user);

        return new AuthResponse(accessToken, refreshToken.getToken(), jwtTokenService.getExpirationMs());
    }

    public AuthResponse login(LoginRequest request) {
        // Behind the scenes process for checking authentication.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Grab the User entity.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow();

        // Generate both tokens and return them.
        String accessToken = jwtTokenService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken(), jwtTokenService.getExpirationMs());
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        // Rotate the refresh token and get the user.
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(refreshTokenValue);
        User user = newRefreshToken.getUser();

        // Generate a fresh access token and return it.
        String accessToken = jwtTokenService.generateAccessToken(user);

        return new AuthResponse(accessToken, newRefreshToken.getToken(), jwtTokenService.getExpirationMs());
    }

    public void logout(String refreshTokenValue) {
        // Revoke the token once past some null and blank checks.
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenService.revoke(refreshTokenValue);
        }
    }
}
