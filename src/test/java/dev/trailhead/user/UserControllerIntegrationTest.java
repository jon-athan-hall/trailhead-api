package dev.trailhead.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.trailhead.role.Role;
import dev.trailhead.role.RoleRepository;
import dev.trailhead.user.dto.AddRoleRequest;
import dev.trailhead.user.dto.ChangePasswordRequest;
import dev.trailhead.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private User otherUser;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        // The H2 in-memory DB is shared across DirtiesContext reloads, so wipe any leftover user data.
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM email_verification_tokens");
        jdbcTemplate.execute("DELETE FROM password_reset_tokens");
        jdbcTemplate.execute("DELETE FROM user_roles");
        jdbcTemplate.execute("DELETE FROM users");

        userRole = roleRepository.findByName("ROLE_USER").orElseThrow();
        adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();

        user = new User();
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setVerified(true);
        user.setRoles(new HashSet<>(List.of(userRole)));
        user = userRepository.save(user);

        otherUser = new User();
        otherUser.setName("Bob");
        otherUser.setEmail("bob@example.com");
        otherUser.setPassword(passwordEncoder.encode("password123"));
        otherUser.setVerified(true);
        otherUser.setRoles(new HashSet<>(List.of(userRole)));
        otherUser = userRepository.save(otherUser);
    }

    // ── Auth helpers ────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser(Long id) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("roles", List.of("ROLE_USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asAdmin(Long id) {
        return jwt().jwt(j -> j.subject(id.toString())
                        .claim("roles", List.of("ROLE_USER", "ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // ── GET /api/users/{id} ─────────────────────────────────────────────

    @Test
    void getUserById_self_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/users/{id}", user.getId()).with(asUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getUserById_otherUser_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/users/{id}", otherUser.getId()).with(asUser(user.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserById_admin_canReadAnyone() throws Exception {
        mockMvc.perform(get("/api/users/{id}", otherUser.getId()).with(asAdmin(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void getUserById_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", user.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserById_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 9999L).with(asAdmin(user.getId())))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/users (list) ───────────────────────────────────────────

    @Test
    void getAllUsers_admin_shouldReturnPage() throws Exception {
        mockMvc.perform(get("/api/users").with(asAdmin(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getAllUsers_nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/users").with(asUser(user.getId())))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/users/{id} ─────────────────────────────────────────────

    @Test
    void updateUser_self_shouldUpdateName() throws Exception {
        var request = new UpdateUserRequest("Alice Updated", null);

        mockMvc.perform(put("/api/users/{id}", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"));
    }

    @Test
    void updateUser_invalidEmail_shouldReturn400() throws Exception {
        var request = new UpdateUserRequest(null, "not-an-email");

        mockMvc.perform(put("/api/users/{id}", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_otherUser_shouldReturn403() throws Exception {
        var request = new UpdateUserRequest("Hacker", null);

        mockMvc.perform(put("/api/users/{id}", otherUser.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_emailAlreadyExists_shouldReturn409() throws Exception {
        var request = new UpdateUserRequest(null, "bob@example.com");

        mockMvc.perform(put("/api/users/{id}", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ── PUT /api/users/{id}/password ────────────────────────────────────

    @Test
    void changePassword_selfWithCorrect_shouldSucceed() throws Exception {
        var request = new ChangePasswordRequest("password123", "newPassword123");

        mockMvc.perform(put("/api/users/{id}/password", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }

    @Test
    void changePassword_selfWithWrong_shouldReturn401() throws Exception {
        var request = new ChangePasswordRequest("wrongpass", "newPassword123");

        mockMvc.perform(put("/api/users/{id}/password", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_adminBypassCurrent_shouldSucceed() throws Exception {
        var request = new ChangePasswordRequest(null, "newPassword123");

        mockMvc.perform(put("/api/users/{id}/password", otherUser.getId())
                        .with(asAdmin(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_tooShort_shouldReturn400() throws Exception {
        var request = new ChangePasswordRequest("password123", "short");

        mockMvc.perform(put("/api/users/{id}/password", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/users/{id}/roles ───────────────────────────────────────

    @Test
    void addRole_admin_shouldSucceed() throws Exception {
        var request = new AddRoleRequest(adminRole.getId());

        mockMvc.perform(put("/api/users/{id}/roles", user.getId())
                        .with(asAdmin(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("ROLE_ADMIN")));
    }

    @Test
    void addRole_nonAdmin_shouldReturn403() throws Exception {
        var request = new AddRoleRequest(adminRole.getId());

        mockMvc.perform(put("/api/users/{id}/roles", user.getId())
                        .with(asUser(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/users/{id}/roles/{roleId} ──────────────────────────

    @Test
    void removeRole_admin_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/users/{id}/roles/{roleId}", user.getId(), userRole.getId())
                        .with(asAdmin(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(0));
    }

    // ── DELETE /api/users/{id} ──────────────────────────────────────────

    @Test
    void deleteUser_self_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", user.getId()).with(asUser(user.getId())))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_otherUser_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", otherUser.getId()).with(asUser(user.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_admin_canDeleteAnyone() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", otherUser.getId()).with(asAdmin(user.getId())))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/users/{id}/restore ───────────────────────────────────

    @Test
    void restoreUser_admin_shouldRestore() throws Exception {
        // Soft-delete first.
        mockMvc.perform(delete("/api/users/{id}", otherUser.getId()).with(asAdmin(user.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/users/{id}/restore", otherUser.getId()).with(asAdmin(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void restoreUser_nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/users/{id}/restore", otherUser.getId()).with(asUser(user.getId())))
                .andExpect(status().isForbidden());
    }
}
