package dev.trailhead.user;

import dev.trailhead.auth.EmailVerificationService;
import dev.trailhead.exception.EmailAlreadyExistsException;
import dev.trailhead.role.Role;
import dev.trailhead.role.RoleRepository;
import dev.trailhead.user.dto.ChangePasswordRequest;
import dev.trailhead.user.dto.UpdateUserRequest;
import dev.trailhead.user.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserMapper userMapper,
                       EmailVerificationService emailVerificationService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (request.name() != null) {
            user.setName(request.name());
        }

        // If the email is changing, ensure it's not taken, reset verified status, and send a new verification email.
        boolean emailChanged = request.email() != null && !user.getEmail().equalsIgnoreCase(request.email());
        if (emailChanged) {
            if (userRepository.existsByEmail(request.email())) {
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email());
            user.setVerified(false);
        }

        user = userRepository.save(user);

        if (emailChanged) {
            emailVerificationService.sendVerificationEmail(user);
        }

        return userMapper.toUserResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        return userMapper.toUserResponseList(userRepository.findAll());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, boolean requireCurrentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Self-service password changes must verify the current password.
        if (requireCurrentPassword) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new BadCredentialsException("Current password is incorrect");
            }
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse addRole(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + roleId));

        user.getRoles().add(role);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse removeRole(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + roleId));

        user.getRoles().remove(role);

        return userMapper.toUserResponse(userRepository.save(user));
    }
}
