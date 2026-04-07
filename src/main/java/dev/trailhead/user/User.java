package dev.trailhead.user;

import dev.trailhead.common.BaseEntity;
import dev.trailhead.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@SoftDelete // Hibernate manages a "deleted" boolean column and filters all queries to exclude deleted rows.
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

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

    @Column(nullable = false)
    private boolean verified;
}
