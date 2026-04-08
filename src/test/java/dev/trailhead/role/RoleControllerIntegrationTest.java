package dev.trailhead.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.trailhead.role.dto.RoleRequest;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.HashSet;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        // The H2 in-memory DB persists across context reloads, so wipe leftover state.
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM email_verification_tokens");
        jdbcTemplate.execute("DELETE FROM password_reset_tokens");
        jdbcTemplate.execute("DELETE FROM user_roles");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM roles WHERE name NOT IN ('ROLE_USER', 'ROLE_ADMIN')");

        userRole = roleRepository.findByName("ROLE_USER").orElseThrow();
        adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
    }

    private RequestPostProcessor asAdmin() {
        return jwt().jwt(j -> j.subject("1").claim("roles", List.of("ROLE_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject("1").claim("roles", List.of("ROLE_USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ── GET /api/roles ──────────────────────────────────────────────────

    @Test
    void getAllRoles_admin_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/roles").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllRoles_nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/roles").with(asUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllRoles_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/roles/{id} ─────────────────────────────────────────────

    @Test
    void getRoleById_admin_shouldReturn() throws Exception {
        mockMvc.perform(get("/api/roles/{id}", userRole.getId()).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ROLE_USER"));
    }

    @Test
    void getRoleById_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/roles/{id}", 9999L).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/roles ─────────────────────────────────────────────────

    @Test
    void createRole_admin_shouldCreate() throws Exception {
        var request = new RoleRequest("ROLE_MODERATOR");

        mockMvc.perform(post("/api/roles")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ROLE_MODERATOR"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createRole_duplicate_shouldReturn409() throws Exception {
        var request = new RoleRequest("ROLE_USER");

        mockMvc.perform(post("/api/roles")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createRole_blankName_shouldReturn400() throws Exception {
        var request = new RoleRequest("");

        mockMvc.perform(post("/api/roles")
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRole_nonAdmin_shouldReturn403() throws Exception {
        var request = new RoleRequest("ROLE_NEW");

        mockMvc.perform(post("/api/roles")
                        .with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/roles/{id} ─────────────────────────────────────────────

    @Test
    void updateRole_admin_shouldUpdate() throws Exception {
        Role role = roleRepository.save(new Role("ROLE_TEMP"));
        var request = new RoleRequest("ROLE_RENAMED");

        mockMvc.perform(put("/api/roles/{id}", role.getId())
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ROLE_RENAMED"));
    }

    @Test
    void updateRole_collidingName_shouldReturn409() throws Exception {
        var request = new RoleRequest("ROLE_ADMIN");

        mockMvc.perform(put("/api/roles/{id}", userRole.getId())
                        .with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // ── DELETE /api/roles/{id} ──────────────────────────────────────────

    @Test
    void deleteRole_admin_unused_shouldDelete() throws Exception {
        Role role = roleRepository.save(new Role("ROLE_TEMP"));

        mockMvc.perform(delete("/api/roles/{id}", role.getId()).with(asAdmin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRole_inUse_shouldReturn409() throws Exception {
        // Assign ROLE_USER to a user so it cannot be deleted.
        User u = new User();
        u.setName("Holder");
        u.setEmail("holder@example.com");
        u.setPassword(passwordEncoder.encode("password123"));
        u.setVerified(true);
        u.setRoles(new HashSet<>(List.of(userRole)));
        userRepository.save(u);

        mockMvc.perform(delete("/api/roles/{id}", userRole.getId()).with(asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRole_notFound_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/roles/{id}", 9999L).with(asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRole_nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/roles/{id}", userRole.getId()).with(asUser()))
                .andExpect(status().isForbidden());
    }
}
