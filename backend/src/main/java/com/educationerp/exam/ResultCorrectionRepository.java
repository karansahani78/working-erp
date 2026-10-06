package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResultCorrectionRepository extends JpaRepository<ResultCorrection, UUID> {

    List<ResultCorrection> findByResultIdOrderByRequestedAtDesc(UUID resultId);

    List<ResultCorrection> findByStatusOrderByRequestedAtAsc(ResultCorrection.Status status);
}
