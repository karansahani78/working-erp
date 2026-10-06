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
public class DepartmentService {
    private final DepartmentRepository repo;
    private final FacultyRepository facRepo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly=true)
    public Page<DepartmentDto> search(String term, UUID facultyId, Pageable pageable) {
        auth.requirePermission("ACADEMIC_READ");
        String t = term==null||term.isBlank()?null:"%"+term.toLowerCase(Locale.ROOT)+"%";
        return repo.search(t,facultyId,pageable).map(DepartmentDto::from);
    }

    @Transactional
    public DepartmentDto create(DepartmentDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        Department d=new Department();
        d.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        d.setName(req.name().trim());
        d.setDescription(req.description());
        if(req.facultyId()!=null) d.setFaculty(facRepo.findById(req.facultyId()).orElse(null));
        d.setActive(req.active());
        return DepartmentDto.from(repo.save(d));
    }

    @Transactional
    public DepartmentDto update(UUID id, DepartmentDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Department d=repo.findById(id).orElseThrow(()->AppException.notFound("Department"));
        d.setName(req.name().trim());
        d.setDescription(req.description());
        if(req.facultyId()!=null) d.setFaculty(facRepo.findById(req.facultyId()).orElse(null));
        else d.setFaculty(null);
        d.setActive(req.active());
        return DepartmentDto.from(repo.save(d));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
