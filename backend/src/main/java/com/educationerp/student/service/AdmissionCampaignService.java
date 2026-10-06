package com.educationerp.student.service;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.student.AdmissionCampaign;
import com.educationerp.student.AdmissionCampaignRepository;
import com.educationerp.student.dto.StudentDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admission campaigns: the window in which applications are accepted for a target
 * academic year, plus the configurable document requirements applicants must satisfy.
 */
@Service
@RequiredArgsConstructor
public class AdmissionCampaignService {

    private final AdmissionCampaignRepository repository;
    private final AuthorizationChecker auth;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<StudentDtos.CampaignResponse> search(String status, Pageable pageable) {
        auth.requirePermission("ADMISSION_READ");
        AdmissionCampaign.Status parsed = (status == null || status.isBlank())
                ? null : parseStatus(status);
        var page = repository.search(parsed, null, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public StudentDtos.CampaignResponse get(UUID id) {
        auth.requirePermission("ADMISSION_READ");
        return toResponse(require(id));
    }

    @Transactional
    public StudentDtos.CampaignResponse create(StudentDtos.CampaignRequest request) {
        auth.requirePermission("ADMISSION_CREATE");
        String code = normaliseCode(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("An admission campaign with code " + code + " already exists.");
        }
        validateDates(request.openDate(), request.closeDate());
        AdmissionCampaign campaign = new AdmissionCampaign();
        campaign.setName(request.name().trim());
        campaign.setCode(code);
        campaign.setAcademicYearId(request.academicYearId());
        campaign.setOpenDate(request.openDate());
        campaign.setCloseDate(request.closeDate());
        campaign.setApplicationFee(request.applicationFee() == null ? java.math.BigDecimal.ZERO : request.applicationFee());
        campaign.setCapacity(request.capacity());
        campaign.setStatus(parseStatus(request.status()));
        campaign.setDocumentRequirements(writeRequirements(request.documentRequirements()));
        return toResponse(repository.save(campaign));
    }

    @Transactional
    public StudentDtos.CampaignResponse update(UUID id, StudentDtos.CampaignRequest request) {
        auth.requirePermission("ADMISSION_UPDATE");
        AdmissionCampaign campaign = require(id);
        validateDates(request.openDate(), request.closeDate());
        campaign.setName(request.name().trim());
        campaign.setAcademicYearId(request.academicYearId());
        campaign.setOpenDate(request.openDate());
        campaign.setCloseDate(request.closeDate());
        if (request.applicationFee() != null) {
            campaign.setApplicationFee(request.applicationFee());
        }
        campaign.setCapacity(request.capacity());
        campaign.setStatus(parseStatus(request.status()));
        campaign.setDocumentRequirements(writeRequirements(request.documentRequirements()));
        return toResponse(repository.save(campaign));
    }

    /** Opens or closes a campaign; the code is the stable external identifier. */
    @Transactional
    public StudentDtos.CampaignResponse changeStatus(UUID id, String status) {
        auth.requirePermission("ADMISSION_UPDATE");
        AdmissionCampaign campaign = require(id);
        AdmissionCampaign.Status next = parseStatus(status);
        campaign.setStatus(next);
        return toResponse(repository.save(campaign));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ADMISSION_UPDATE");
        repository.delete(require(id));
    }

    AdmissionCampaign require(UUID id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("Admission campaign"));
    }

    private void validateDates(java.time.LocalDate open, java.time.LocalDate close) {
        if (open != null && close != null && close.isBefore(open)) {
            throw AppException.rule("The campaign close date cannot be before its open date.");
        }
    }

    private AdmissionCampaign.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return AdmissionCampaign.Status.PLANNED;
        }
        try {
            return AdmissionCampaign.Status.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unknown campaign status: " + status);
        }
    }

    private String normaliseCode(String code) {
        return code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String writeRequirements(List<String> requirements) {
        if (requirements == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(requirements);
        } catch (JsonProcessingException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Document requirements could not be stored.", ex);
        }
    }

    private List<String> readRequirements(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    StudentDtos.CampaignResponse toResponse(AdmissionCampaign campaign) {
        return new StudentDtos.CampaignResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getCode(),
                campaign.getAcademicYearId(),
                campaign.getOpenDate(),
                campaign.getCloseDate(),
                campaign.getApplicationFee(),
                campaign.getCapacity(),
                campaign.getStatus().name(),
                readRequirements(campaign.getDocumentRequirements()));
    }
}