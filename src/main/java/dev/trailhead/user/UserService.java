package dev.trailhead.user;

import dev.trailhead.role.Role;
import dev.trailhead.role.RoleRepository;
import dev.trailhead.user.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
    }

    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        return userMapper.toUserResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        return userMapper.toUserResponseList(userRepository.findAll());
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
