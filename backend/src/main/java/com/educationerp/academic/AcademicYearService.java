package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.Enums;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcademicYearService {
    private final AcademicYearRepository repo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly=true)
    public Page<AcademicYearDto> search(String term, Pageable pageable) {
        auth.requirePermission("ACADEMIC_READ");
        String t = term==null||term.isBlank()?null:"%"+term.toLowerCase(Locale.ROOT)+"%";
        return repo.search(t,pageable).map(AcademicYearDto::from);
    }

    @Transactional
    public AcademicYearDto create(AcademicYearDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        AcademicYear y=new AcademicYear();
        y.setName(req.name().trim());
        y.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        y.setStartDate(req.startDate());
        y.setEndDate(req.endDate());
        y.setCalendar(Enums.parse(AcademicYear.CalendarType.class, req.calendar(), "calendar"));
        y.setStatus(Enums.parse(AcademicYear.Status.class, req.status(), "status",
                AcademicYear.Status.PLANNED));
        y.setCurrent(req.current());
        return AcademicYearDto.from(repo.save(y));
    }

    @Transactional
    public AcademicYearDto update(UUID id, AcademicYearDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        AcademicYear y=repo.findById(id).orElseThrow(()->AppException.notFound("AcademicYear"));
        y.setName(req.name().trim());
        y.setStartDate(req.startDate());
        y.setEndDate(req.endDate());
        y.setCalendar(Enums.parse(AcademicYear.CalendarType.class, req.calendar(), "calendar"));
        y.setStatus(Enums.parse(AcademicYear.Status.class, req.status(), "status",
                AcademicYear.Status.PLANNED));
        y.setCurrent(req.current());
        return AcademicYearDto.from(repo.save(y));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
