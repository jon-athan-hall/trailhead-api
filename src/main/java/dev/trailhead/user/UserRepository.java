package dev.trailhead.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // Lightweight check with no entity hydration and no role joins.
    boolean existsByEmail(String email);
}
