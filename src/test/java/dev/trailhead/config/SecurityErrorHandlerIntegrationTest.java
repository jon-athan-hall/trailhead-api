package dev.trailhead.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── 401: missing/invalid credentials ───────────────────────────────

    @Test
    void noToken_shouldReturn401WithJsonErrorBody() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/users/1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidBearerToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users/1").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ── 403: authenticated but lacking authority ───────────────────────

    @Test
    void authenticatedWithoutRequiredRole_shouldReturn403WithJsonErrorBody() throws Exception {
        // /api/users (list) requires ROLE_ADMIN; this user has only ROLE_USER.
        mockMvc.perform(get("/api/users")
                        .with(jwt().jwt(j -> j.subject("1").claim("roles", List.of("ROLE_USER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
