package dev.trailhead.user;

import dev.trailhead.user.dto.AddRoleRequest;
import dev.trailhead.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> addRole(@PathVariable Long id,
                                                @Valid @RequestBody AddRoleRequest request) {
        return ResponseEntity.ok(userService.addRole(id, request.roleId()));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> removeRole(@PathVariable Long id,
                                                   @PathVariable Long roleId) {
        return ResponseEntity.ok(userService.removeRole(id, roleId));
    }
}
