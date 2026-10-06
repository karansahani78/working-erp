package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SchoolClassService {
    private final SchoolClassRepository repo;
    private final AcademicYearRepository yearRepo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly=true)
    public Page<SchoolClassDto> search(UUID yearId, String term, Pageable pageable) {
        auth.requirePermission("ACADEMIC_READ");
        String t = term==null||term.isBlank()?null:"%"+term.toLowerCase(Locale.ROOT)+"%";
        return repo.search(yearId,t,pageable).map(SchoolClassDto::from);
    }

    @Transactional
    public SchoolClassDto create(SchoolClassDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        SchoolClass c=new SchoolClass();
        c.setAcademicYear(yearRepo.findById(req.academicYearId()).orElseThrow(()->AppException.notFound("AcademicYear")));
        c.setName(req.name().trim());
        c.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        c.setOrdinal(req.ordinal());
        c.setActive(req.active());
        return SchoolClassDto.from(repo.save(c));
    }

    @Transactional
    public SchoolClassDto update(UUID id, SchoolClassDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        SchoolClass c=repo.findById(id).orElseThrow(()->AppException.notFound("SchoolClass"));
        c.setName(req.name().trim());
        c.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        c.setOrdinal(req.ordinal());
        c.setActive(req.active());
        return SchoolClassDto.from(repo.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
