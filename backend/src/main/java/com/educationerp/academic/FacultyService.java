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
public class FacultyService {
    private final FacultyRepository repo;
    private final CampusRepository campusRepo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public Page<FacultyDto> search(String term, Pageable pageable) {
        auth.requirePermission("ACADEMIC_READ");
        String t = term==null||term.isBlank()?null:"%"+term.toLowerCase(Locale.ROOT)+"%";
        return repo.search(t, pageable).map(FacultyDto::from);
    }

    @Transactional
    public FacultyDto create(FacultyDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        Faculty f=new Faculty();
        f.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        f.setName(req.name().trim());
        f.setDescription(req.description());
        if(req.campusId()!=null) f.setCampus(campusRepo.findById(req.campusId()).orElse(null));
        f.setActive(req.active());
        return FacultyDto.from(repo.save(f));
    }

    @Transactional
    public FacultyDto update(UUID id, FacultyDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Faculty f=repo.findById(id).orElseThrow(()->AppException.notFound("Faculty"));
        f.setName(req.name().trim());
        f.setDescription(req.description());
        if(req.campusId()!=null) f.setCampus(campusRepo.findById(req.campusId()).orElse(null));
        else f.setCampus(null);
        f.setActive(req.active());
        return FacultyDto.from(repo.save(f));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
