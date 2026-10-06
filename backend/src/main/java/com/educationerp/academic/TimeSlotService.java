package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeSlotService {
    private final TimeSlotRepository repo;
    private final AuthorizationChecker auth;

    @Transactional(readOnly=true)
    public List<TimeSlotDto> listActive() {
        auth.requirePermission("ACADEMIC_READ");
        return repo.findByActiveTrueOrderByOrdinalAsc().stream().map(TimeSlotDto::from).toList();
    }

    @Transactional
    public TimeSlotDto create(TimeSlotDto req) {
        auth.requirePermission("ACADEMIC_CREATE");
        TimeSlot t=new TimeSlot();
        t.setName(req.name().trim());
        t.setStartTime(req.startTime());
        t.setEndTime(req.endTime());
        if(req.slotType()!=null) t.setSlotType(TimeSlot.SlotType.valueOf(req.slotType()));
        t.setOrdinal(req.ordinal());
        t.setActive(req.active());
        return TimeSlotDto.from(repo.save(t));
    }

    @Transactional
    public TimeSlotDto update(UUID id, TimeSlotDto req) {
        auth.requirePermission("ACADEMIC_UPDATE");
        TimeSlot t=repo.findById(id).orElseThrow(()->AppException.notFound("TimeSlot"));
        t.setName(req.name().trim());
        t.setStartTime(req.startTime());
        t.setEndTime(req.endTime());
        if(req.slotType()!=null) t.setSlotType(TimeSlot.SlotType.valueOf(req.slotType()));
        t.setOrdinal(req.ordinal());
        t.setActive(req.active());
        return TimeSlotDto.from(repo.save(t));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ACADEMIC_DELETE");
        repo.deleteById(id);
    }
}
