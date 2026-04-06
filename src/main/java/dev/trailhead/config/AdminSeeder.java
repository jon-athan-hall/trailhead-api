package dev.trailhead.config;

import dev.trailhead.role.Role;
import dev.trailhead.role.RoleRepository;
import dev.trailhead.user.User;
import dev.trailhead.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email}")
    private String adminEmail;

    @Value("${admin.seed.password:}")
    private String adminPassword;

    public AdminSeeder(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Skip seeding if no password is configured.
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin seed skipped: ADMIN_SEED_PASSWORD not set");
            return;
        }

        // Skip seeding if the admin account already exists.
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin seed skipped: {} already exists", adminEmail);
            return;
        }

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found"));
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found"));

        User admin = new User();
        admin.setName("Admin");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setVerified(true);
        admin.getRoles().add(userRole);
        admin.getRoles().add(adminRole);

        userRepository.save(admin);
        log.info("Admin account seeded: {}", adminEmail);
    }
}
