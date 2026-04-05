package dev.trailhead.user;

import dev.trailhead.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER) // Load roles immediately when a user is loaded from the db.
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"), // Foreign key for this entity.
            inverseJoinColumns = @JoinColumn(name = "role_id") // Foreign key for the other entity.
    )
    private Set<Role> roles = new HashSet<>(); // Set prevents duplicate roles.

    @Column(name = "created_at", nullable = false, updatable = false) // Exclude when updating.
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Runs right before the first insert.
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    // Runs right before any update.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
