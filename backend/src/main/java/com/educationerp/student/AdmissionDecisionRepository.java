package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdmissionDecisionRepository extends JpaRepository<AdmissionDecision, UUID> {

    List<AdmissionDecision> findByApplicationIdOrderByDecidedAtDesc(UUID applicationId);
}