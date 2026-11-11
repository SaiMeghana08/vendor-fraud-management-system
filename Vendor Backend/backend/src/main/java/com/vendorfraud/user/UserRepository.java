package com.vendorfraud.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndActiveTrue(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Active holders of a role, ordered for stable tie-breaking. */
    @Query("""
           select u from User u join u.roles r
            where r.name = :roleName and u.active = true
            order by u.id asc
           """)
    List<User> findActiveByRole(@Param("roleName") RoleName roleName);

    @Query("select u from User u where u.active = true order by u.fullName asc")
    List<User> findAllActive();
}
