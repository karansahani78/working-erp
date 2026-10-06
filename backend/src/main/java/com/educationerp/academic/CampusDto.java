package com.educationerp.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CampusDto(
        java.util.UUID id,
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 400) String address,
        @Size(max = 60) String phone,
        boolean active
) {
    public static CampusDto from(Campus c) {
        return new CampusDto(c.getId(), c.getCode(), c.getName(), c.getAddress(), c.getPhone(), c.isActive());
    }
}
