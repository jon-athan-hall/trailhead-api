package dev.trailhead.role;

import dev.trailhead.exception.RoleInUseException;
import dev.trailhead.exception.RoleNameAlreadyExistsException;
import dev.trailhead.role.dto.RoleRequest;
import dev.trailhead.role.dto.RoleResponse;
import dev.trailhead.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleMapper roleMapper;

    public RoleService(RoleRepository roleRepository,
                       UserRepository userRepository,
                       RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
    }

    public List<RoleResponse> getAllRoles() {
        return roleMapper.toRoleResponseList(roleRepository.findAll());
    }

    public RoleResponse getRoleById(String id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));
        return roleMapper.toRoleResponse(role);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new RoleNameAlreadyExistsException(request.name());
        }
        Role role = new Role(request.name());
        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(String id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));

        if (!role.getName().equals(request.name()) && roleRepository.existsByName(request.name())) {
            throw new RoleNameAlreadyExistsException(request.name());
        }

        role.setName(request.name());
        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(String id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));

        if (userRepository.existsByRolesId(id)) {
            throw new RoleInUseException(id);
        }

        roleRepository.delete(role);
    }
}
