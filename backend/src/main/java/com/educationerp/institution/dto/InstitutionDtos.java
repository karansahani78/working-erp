package com.educationerp.institution.dto;

import com.educationerp.institution.AcademicModel;
import com.educationerp.institution.Institution;
import com.educationerp.institution.InstitutionType;
import com.educationerp.institution.ModuleKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class InstitutionDtos {

    private InstitutionDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstitutionResponse(
            java.util.UUID id,
            String name,
            String shortName,
            String institutionCode,
            InstitutionType institutionType,
            String logoUrl,
            String faviconUrl,
            String primaryColor,
            String secondaryColor,
            String address,
            String municipality,
            String district,
            String province,
            String country,
            String phone,
            String email,
            String website,
            String timezone,
            String currency,
            LocalDate fiscalYearStart,
            String dateFormat,
            AcademicModel academicModel,
            String portalTitle,
            String portalDescription,
            String supportEmail,
            String supportPhone,
            boolean setupCompleted
    ) {
        public static InstitutionResponse from(Institution i) {
            return new InstitutionResponse(
                    i.getId(), i.getName(), i.getShortName(), i.getInstitutionCode(), i.getInstitutionType(),
                    i.getLogoUrl(), i.getFaviconUrl(), i.getPrimaryColor(), i.getSecondaryColor(),
                    i.getAddress(), i.getMunicipality(), i.getDistrict(), i.getProvince(), i.getCountry(),
                    i.getPhone(), i.getEmail(), i.getWebsite(), i.getTimezone(), i.getCurrency(),
                    i.getFiscalYearStart(), i.getDateFormat(), i.getAcademicModel(),
                    i.getPortalTitle(), i.getPortalDescription(),
                    i.getSupportEmail(), i.getSupportPhone(), i.isSetupCompleted());
        }
    }

    public record UpdateInstitutionRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 40) String shortName,
            InstitutionType institutionType,
            @Size(max = 500) String logoUrl,
            @Size(max = 500) String faviconUrl,
            @Pattern(regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$", message = "Enter a valid hex colour.")
            String primaryColor,
            @Pattern(regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$", message = "Enter a valid hex colour.")
            String secondaryColor,
            @Size(max = 400) String address,
            @Size(max = 120) String municipality,
            @Size(max = 120) String district,
            @Size(max = 120) String province,
            @Size(max = 80) String country,
            @Size(max = 60) String phone,
            @Email @Size(max = 180) String email,
            @Size(max = 200) String website,
            @Size(max = 80) String timezone,
            @Size(max = 10) String currency,
            LocalDate fiscalYearStart,
            @Pattern(regexp = "^(AD|BS)$", message = "Date format must be AD or BS.") String dateFormat,
            AcademicModel academicModel,
            @Size(max = 120) String portalTitle,
            @Size(max = 400) String portalDescription,
            @Email @Size(max = 180) String supportEmail,
            @Size(max = 60) String supportPhone
    ) {
    }

    public record ModuleToggleResponse(ModuleKey moduleKey, boolean enabled, boolean implemented) {
    }

    public record ModuleToggleItem(@NotNull ModuleKey moduleKey, @NotNull Boolean enabled) {
    }

    public record ModuleToggleBatchRequest(@NotNull @Size(min = 1) List<@Valid ModuleToggleItem> modules) {
    }
}
