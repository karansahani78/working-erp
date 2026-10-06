package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;
import java.util.UUID;

public record TimeSlotDto(UUID id, @NotBlank String name, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                          String slotType, @NotNull @Positive Integer ordinal, boolean active) {
    public static TimeSlotDto from(TimeSlot t) {
        return new TimeSlotDto(t.getId(), t.getName(), t.getStartTime(), t.getEndTime(), t.getSlotType().name(), t.getOrdinal(), t.isActive());
    }
}
