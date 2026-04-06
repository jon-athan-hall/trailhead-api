package dev.trailhead.role;

import dev.trailhead.role.dto.RoleResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public RoleService(RoleRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    public List<RoleResponse> getAllRoles() {
        return roleMapper.toRoleResponseList(roleRepository.findAll());
    }
}
