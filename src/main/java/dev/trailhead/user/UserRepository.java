package dev.trailhead.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // Lightweight check with no entity hydration and no role joins.
    boolean existsByEmail(String email);

    // Used to prevent deleting a role that is still assigned to one or more users.
    boolean existsByRolesId(Long roleId);

    // Native query to find a soft-deleted user by ID. Bypasses Hibernate's @SoftDelete filter.
    @Query(value = "SELECT * FROM users WHERE id = :id AND deleted = TRUE", nativeQuery = true)
    Optional<User> findDeletedById(@Param("id") Long id);

    // Native update to restore a soft-deleted user. Returns the number of rows affected.
    @Modifying
    @Query(value = "UPDATE users SET deleted = FALSE WHERE id = :id AND deleted = TRUE", nativeQuery = true)
    int restoreById(@Param("id") Long id);
}
