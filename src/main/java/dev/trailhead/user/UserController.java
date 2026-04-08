package dev.trailhead.user;

import dev.trailhead.auth.dto.MessageResponse;
import dev.trailhead.user.dto.AddRoleRequest;
import dev.trailhead.user.dto.ChangePasswordRequest;
import dev.trailhead.user.dto.UpdateUserRequest;
import dev.trailhead.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> updateUser(@PathVariable String id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<MessageResponse> changePassword(@PathVariable String id,
                                                          @Valid @RequestBody ChangePasswordRequest request,
                                                          Authentication authentication) {
        // Self-service password changes must verify the current password.
        // Admins changing another user's password do not.
        boolean isSelf = id.toString().equals(authentication.getName());
        userService.changePassword(id, request, isSelf);
        return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> addRole(@PathVariable String id,
                                                @Valid @RequestBody AddRoleRequest request) {
        return ResponseEntity.ok(userService.addRole(id, request.roleId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> restoreUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.restoreUser(id));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<UserResponse> removeRole(@PathVariable String id,
                                                   @PathVariable String roleId) {
        return ResponseEntity.ok(userService.removeRole(id, roleId));
    }
}
