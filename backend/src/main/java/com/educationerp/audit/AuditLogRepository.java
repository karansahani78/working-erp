package com.educationerp.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * The trail is append-only, so this repository deliberately exposes no update or delete.
 *
 * <p>Reading uses a {@link JpaSpecificationExecutor} rather than a hand-written
 * {@code :param is null} query: PostgreSQL cannot infer the type of a parameter that is only
 * ever compared to null, which makes the literal-query form fail at execution time.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Query("select a.action, count(a) from AuditLog a group by a.action")
    List<Object[]> countByAction();
}