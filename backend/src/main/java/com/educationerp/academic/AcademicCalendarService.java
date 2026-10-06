package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicCalendarService {
    private final AcademicCalendarEventRepository repo;
    private final AcademicYearRepository yearRepo;
    private final SemesterRepository semRepo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly=true)
    public List<AcademicCalendarDto> list(UUID yearId) {
        auth.requirePermission("ACADEMIC_READ");
        return repo.findByAcademicYearIdOrderByStartDateAsc(yearId).stream().map(AcademicCalendarDto::from).toList();
    }

    @Transactional
    public AcademicCalendarDto create(AcademicCalendarDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        AcademicCalendarEvent e=new AcademicCalendarEvent();
        e.setTitle(req.title().trim());
        e.setEventType(AcademicCalendarEvent.EventType.valueOf(req.eventType()));
        e.setAcademicYear(yearRepo.findById(req.academicYearId()).orElseThrow(()->AppException.notFound("AcademicYear")));
        if(req.semesterId()!=null) e.setSemester(semRepo.findById(req.semesterId()).orElse(null));
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setWorkingDay(req.workingDay());
        e.setDescription(req.description());
        return AcademicCalendarDto.from(repo.save(e));
    }

    @Transactional
    public AcademicCalendarDto update(UUID id, AcademicCalendarDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        AcademicCalendarEvent e=repo.findById(id).orElseThrow(()->AppException.notFound("AcademicCalendarEvent"));
        e.setTitle(req.title().trim());
        e.setEventType(AcademicCalendarEvent.EventType.valueOf(req.eventType()));
        if(req.semesterId()!=null) e.setSemester(semRepo.findById(req.semesterId()).orElse(null));
        else e.setSemester(null);
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setWorkingDay(req.workingDay());
        e.setDescription(req.description());
        return AcademicCalendarDto.from(repo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
