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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role("ROLE_USER");
        userRole.setId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    // ── register ───────────────────────────────────────────────────────

    @Test
    void register_newUser_shouldReturnTokensAndSendVerification() {
        var request = new RegisterRequest("Test", "new@example.com", "password123");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("11111111-1111-1111-1111-111111111111");
            return u;
        });
        when(jwtTokenService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenService.getExpirationMs()).thenReturn(900000L);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(refreshToken);

        AuthResponse response = authService.register(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(900000L, response.expiresIn());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("encoded", savedUser.getPassword());
        assertTrue(savedUser.getRoles().contains(userRole));

        verify(emailVerificationService).sendVerificationEmail(any(User.class));
    }

    @Test
    void register_existingEmail_shouldThrow() {
        var request = new RegisterRequest("Test", "dup@example.com", "password123");
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).sendVerificationEmail(any());
    }

    @Test
    void register_defaultRoleMissing_shouldThrow() {
        var request = new RegisterRequest("Test", "new@example.com", "password123");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> authService.register(request));
    }

    // ── login ──────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_shouldReturnTokens() {
        var request = new LoginRequest("test@example.com", "password123");

        User user = new User();
        user.setId("22222222-2222-2222-2222-222222222222");
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtTokenService.getExpirationMs()).thenReturn(900000L);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);

        AuthResponse response = authService.login(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_badCredentials_shouldPropagate() {
        var request = new LoginRequest("test@example.com", "wrong");
        doThrow(new BadCredentialsException("bad creds"))
                .when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    // ── refresh ────────────────────────────────────────────────────────

    @Test
    void refresh_shouldRotateAndReturnNewTokens() {
        User user = new User();
        user.setId("22222222-2222-2222-2222-222222222222");
        user.setEmail("test@example.com");

        RefreshToken rotated = new RefreshToken();
        rotated.setToken("new-refresh");
        rotated.setUser(user);

        when(refreshTokenService.rotateRefreshToken("old-refresh")).thenReturn(rotated);
        when(jwtTokenService.generateAccessToken(user)).thenReturn("new-access");
        when(jwtTokenService.getExpirationMs()).thenReturn(900000L);

        AuthResponse response = authService.refresh("old-refresh");

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        verify(refreshTokenService).rotateRefreshToken("old-refresh");
    }

    // ── logout ─────────────────────────────────────────────────────────

    @Test
    void logout_validToken_shouldRevoke() {
        authService.logout("some-token");
        verify(refreshTokenService).revoke("some-token");
    }

    @Test
    void logout_nullToken_shouldNoop() {
        authService.logout(null);
        verify(refreshTokenService, never()).revoke(any());
    }

    @Test
    void logout_blankToken_shouldNoop() {
        authService.logout("   ");
        verify(refreshTokenService, never()).revoke(any());
    }
}
