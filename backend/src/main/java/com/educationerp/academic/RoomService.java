package com.educationerp.academic;

import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Physical rooms. A room's capacity decides whether a class can be scheduled into it. */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository rooms;
    private final CampusRepository campuses;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.RoomResponse> list(UUID campusId) {
        auth.requirePermission("ACADEMIC_READ");
        List<Room> found = campusId != null
                ? rooms.findByCampusIdAndActiveTrueOrderByNameAsc(campusId)
                : rooms.findByActiveTrueOrderByNameAsc();
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AcademicStructureDto.RoomResponse create(AcademicStructureDto.CreateRoom request) {
        auth.requirePermission("ACADEMIC_CREATE");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (rooms.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("A room with the code " + code + " already exists.");
        }
        Room room = new Room();
        room.setCode(code);
        room.setName(request.name().trim());
        room.setBuilding(trimToNull(request.building()));
        room.setCapacity(request.capacity());
        room.setRoomType(trimToNull(request.roomType()));
        if (request.campusId() != null) {
            room.setCampus(campuses.findById(request.campusId())
                    .orElseThrow(() -> AppException.notFound("Campus")));
        }
        room.setActive(true);
        return toResponse(rooms.save(room));
    }

    @Transactional
    public AcademicStructureDto.RoomResponse update(UUID id, AcademicStructureDto.CreateRoom request) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Room room = rooms.findById(id).orElseThrow(() -> AppException.notFound("Room"));
        // The code is the room's identity in timetables and printed schedules.
        room.setName(request.name().trim());
        room.setBuilding(trimToNull(request.building()));
        room.setCapacity(request.capacity());
        room.setRoomType(trimToNull(request.roomType()));
        return toResponse(rooms.save(room));
    }

    @Transactional
    public AcademicStructureDto.RoomResponse deactivate(UUID id) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Room room = rooms.findById(id).orElseThrow(() -> AppException.notFound("Room"));
        room.setActive(false);
        return toResponse(rooms.save(room));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AcademicStructureDto.RoomResponse toResponse(Room room) {
        return new AcademicStructureDto.RoomResponse(room.getId(), room.getCode(), room.getName(),
                room.getBuilding(), room.getCapacity(), room.getRoomType(),
                room.getCampus() == null ? null : room.getCampus().getId(),
                room.getCampus() == null ? null : room.getCampus().getName(),
                room.isActive());
    }
}
