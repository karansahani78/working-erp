package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FineRepository extends JpaRepository<Fine, UUID> {

    List<Fine> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Fine> findByStatusOrderByCreatedAtAsc(Fine.Status status);
}
