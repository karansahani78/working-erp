package com.educationerp.auth.user;

import com.educationerp.auth.role.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("""
            select u from User u
            where lower(u.username) = lower(:identifier)
               or (u.email is not null and lower(u.email) = lower(:identifier))
            """)
    Optional<User> findByLoginIdentifier(@Param("identifier") String identifier);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByStudentId(UUID studentId);

    Optional<User> findByEmployeeId(UUID employeeId);

    @Query("""
            select u from User u
            where (:term is null or lower(u.username) like :term
                            or lower(u.displayName) like :term
                            or lower(coalesce(u.email,'')) like :term)
              and (:role is null or u.primaryRole = :role)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("term") String term,
                      @Param("role") Role role,
                      @Param("status") UserStatus status,
                      Pageable pageable);

    List<User> findByStatus(UserStatus status);

    /** The whole audience for a notice, addressed by the role people sign in under. */
    List<User> findByPrimaryRoleAndStatus(Role role, UserStatus status);

    long countByStatus(UserStatus status);
}
