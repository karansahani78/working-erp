package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
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
public class CourseService {
    private final CourseRepository repo;
    private final DepartmentRepository deptRepo;
    private final ProgramRepository progRepo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly=true)
    public Page<CourseDto> search(String term, Pageable pageable) {
        auth.requirePermission("ACADEMIC_READ");
        String t = term==null||term.isBlank()?null:"%"+term.toLowerCase(Locale.ROOT)+"%";
        return repo.search(t,null,null,null,pageable).map(CourseDto::from);
    }

    @Transactional
    public CourseDto create(CourseDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        Course c=new Course();
        c.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        c.setName(req.name().trim());
        c.setDescription(req.description());
        if(req.courseType()!=null) c.setCourseType(Course.CourseType.valueOf(req.courseType()));
        if(req.departmentId()!=null) c.setDepartment(deptRepo.findById(req.departmentId()).orElse(null));
        if(req.programId()!=null) c.setProgram(progRepo.findById(req.programId()).orElse(null));
        c.setCreditHours(req.creditHours());
        c.setActive(req.active());
        return CourseDto.from(repo.save(c));
    }

    @Transactional
    public CourseDto update(UUID id, CourseDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Course c=repo.findById(id).orElseThrow(()->AppException.notFound("Course"));
        c.setName(req.name().trim());
        c.setDescription(req.description());
        if(req.courseType()!=null) c.setCourseType(Course.CourseType.valueOf(req.courseType()));
        if(req.departmentId()!=null) c.setDepartment(deptRepo.findById(req.departmentId()).orElse(null));
        else c.setDepartment(null);
        if(req.programId()!=null) c.setProgram(progRepo.findById(req.programId()).orElse(null));
        else c.setProgram(null);
        c.setCreditHours(req.creditHours());
        c.setActive(req.active());
        return CourseDto.from(repo.save(c));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
