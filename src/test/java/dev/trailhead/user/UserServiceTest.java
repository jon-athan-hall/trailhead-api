package dev.trailhead.user;

import dev.trailhead.auth.EmailVerificationService;
import dev.trailhead.exception.EmailAlreadyExistsException;
import dev.trailhead.role.Role;
import dev.trailhead.role.RoleRepository;
import dev.trailhead.token.RefreshTokenRepository;
import dev.trailhead.user.dto.ChangePasswordRequest;
import dev.trailhead.user.dto.UpdateUserRequest;
import dev.trailhead.user.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_USER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String USER_ROLE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String ADMIN_ROLE_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserResponse testUserResponse;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(USER_ID);
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encoded-password");
        testUser.setVerified(true);
        testUser.setRoles(new HashSet<>(Set.of(new Role("ROLE_USER"))));

        testUserResponse = new UserResponse(USER_ID, "Test User", "test@example.com",
                true, Set.of("ROLE_USER"), null);
    }

    @Test
    void getUserById_whenExists_shouldReturnUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

        UserResponse result = userService.getUserById(USER_ID);

        assertEquals(testUserResponse, result);
    }

    @Test
    void getUserById_whenNotFound_shouldThrow() {
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.getUserById(OTHER_USER_ID));
    }

    @Test
    void updateUser_nameOnly_shouldUpdate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(testUserResponse);

        userService.updateUser(USER_ID, new UpdateUserRequest("New Name", null));

        assertEquals("New Name", testUser.getName());
        verify(emailVerificationService, never()).sendVerificationEmail(any());
    }

    @Test
    void updateUser_changingEmail_shouldResetVerifiedAndSendEmail() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(testUserResponse);

        userService.updateUser(USER_ID, new UpdateUserRequest(null, "new@example.com"));

        assertEquals("new@example.com", testUser.getEmail());
        assertFalse(testUser.isVerified());
        verify(emailVerificationService).sendVerificationEmail(testUser);
    }

    @Test
    void updateUser_emailAlreadyTaken_shouldThrow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> userService.updateUser(USER_ID, new UpdateUserRequest(null, "taken@example.com")));

        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).sendVerificationEmail(any());
    }

    @Test
    void updateUser_sameEmailDifferentCase_shouldNotResetVerified() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(testUserResponse);

        userService.updateUser(USER_ID, new UpdateUserRequest(null, "TEST@example.com"));

        assertTrue(testUser.isVerified());
        verify(emailVerificationService, never()).sendVerificationEmail(any());
    }

    @Test
    void getAllUsers_shouldReturnPageOfResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(testUser));
        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

        Page<UserResponse> result = userService.getAllUsers(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(testUserResponse, result.getContent().get(0));
    }

    @Test
    void changePassword_selfWithCorrectCurrent_shouldUpdate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPass", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("new-encoded");

        userService.changePassword(USER_ID, new ChangePasswordRequest("oldPass", "newPass123"), true);

        assertEquals("new-encoded", testUser.getPassword());
        verify(userRepository).save(testUser);
    }

    @Test
    void changePassword_selfWithWrongCurrent_shouldThrow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> userService.changePassword(USER_ID, new ChangePasswordRequest("wrong", "newPass123"), true));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_selfWithNullCurrent_shouldThrow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        assertThrows(BadCredentialsException.class,
                () -> userService.changePassword(USER_ID, new ChangePasswordRequest(null, "newPass123"), true));
    }

    @Test
    void changePassword_adminBypassCurrent_shouldUpdate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPass123")).thenReturn("new-encoded");

        userService.changePassword(USER_ID, new ChangePasswordRequest(null, "newPass123"), false);

        assertEquals("new-encoded", testUser.getPassword());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository).save(testUser);
    }

    @Test
    void addRole_shouldAddRoleToUser() {
        Role admin = new Role("ROLE_ADMIN");
        admin.setId(ADMIN_ROLE_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(ADMIN_ROLE_ID)).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(testUserResponse);

        userService.addRole(USER_ID, ADMIN_ROLE_ID);

        assertTrue(testUser.getRoles().contains(admin));
    }

    @Test
    void addRole_userNotFound_shouldThrow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.addRole(USER_ID, ADMIN_ROLE_ID));
    }

    @Test
    void addRole_roleNotFound_shouldThrow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(ADMIN_ROLE_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.addRole(USER_ID, ADMIN_ROLE_ID));
    }

    @Test
    void removeRole_shouldRemoveRoleFromUser() {
        Role userRole = testUser.getRoles().iterator().next();
        userRole.setId(USER_ROLE_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(roleRepository.findById(USER_ROLE_ID)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toUserResponse(any(User.class))).thenReturn(testUserResponse);

        userService.removeRole(USER_ID, USER_ROLE_ID);

        assertFalse(testUser.getRoles().contains(userRole));
    }

    @Test
    void deleteUser_shouldRevokeTokensAndDelete() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));

        userService.deleteUser(USER_ID);

        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteUser_notFound_shouldThrow() {
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.deleteUser(OTHER_USER_ID));
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void restoreUser_whenRowsAffected_shouldReturnUser() {
        when(userRepository.restoreById(USER_ID)).thenReturn(1);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
        when(userMapper.toUserResponse(testUser)).thenReturn(testUserResponse);

        UserResponse result = userService.restoreUser(USER_ID);

        assertEquals(testUserResponse, result);
    }

    @Test
    void restoreUser_whenNoRowsAffected_shouldThrow() {
        when(userRepository.restoreById(USER_ID)).thenReturn(0);

        assertThrows(EntityNotFoundException.class, () -> userService.restoreUser(USER_ID));
    }
}
