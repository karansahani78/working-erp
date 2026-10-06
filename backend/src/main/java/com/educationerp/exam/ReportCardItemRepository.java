package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportCardItemRepository extends JpaRepository<ReportCardItem, UUID> {

    List<ReportCardItem> findByReportCardIdOrderBySubjectNameAsc(UUID reportCardId);
}
