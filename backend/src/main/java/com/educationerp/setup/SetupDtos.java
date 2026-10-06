package com.educationerp.setup;

import com.educationerp.institution.AcademicModel;
import com.educationerp.institution.InstitutionType;
import com.educationerp.institution.ModuleKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Payloads for the first-run setup wizard. The wizard is a single transactional
 * submission: institution, branding, academic structure, administrator and enabled
 * modules are created together so an installation can never be half configured.
 */
public final class SetupDtos {

    private SetupDtos() {
    }

    /** Step 1 — institution identity. */
    public record InstitutionStep(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 40) String shortName,
            @NotBlank @Size(max = 40) String institutionCode,
            @NotNull InstitutionType institutionType
    ) {
    }

    /** Step 2 — academic model. */
    public record AcademicStep(@NotNull AcademicModel academicModel) {
    }

    /** Step 3 — contact and address. */
    public record ContactStep(
            @Size(max = 400) String address,
            @Size(max = 120) String municipality,
            @Size(max = 120) String district,
            @Size(max = 120) String province,
            @Size(max = 80) String country,
            @Size(max = 60) String phone,
            @Email @Size(max = 180) String email,
            @Size(max = 200) String website,
            @Size(max = 180) String supportEmail,
            @Size(max = 60) String supportPhone,
            @Size(max = 120) String portalTitle,
            @Size(max = 400) String portalDescription
    ) {
    }

    /** Step 4 — branding. */
    public record BrandingStep(
            @Size(max = 500) String logoUrl,
            @Size(max = 500) String faviconUrl,
            @Pattern(regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$", message = "Enter a valid hex colour.")
            String primaryColor,
            @Pattern(regexp = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$", message = "Enter a valid hex colour.")
            String secondaryColor
    ) {
    }

    /** Step 5 — first academic year. */
    public record AcademicYearStep(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Pattern(regexp = "^(AD|BS)$", message = "Calendar must be AD or BS.") String calendar
    ) {
    }

    /**
     * Step 6 — academic structure. For SCHOOL the wizard creates classes; for
     * COLLEGE/UNIVERSITY it creates faculty, department and programs. HYBRID accepts both.
     */
    public record AcademicStructureStep(
            List<@Valid ClassSeed> classes,
            List<@Valid FacultySeed> faculties,
            List<@Valid DepartmentSeed> departments,
            List<@Valid ProgramSeed> programs
    ) {
    }

    public record ClassSeed(
            @NotBlank @Size(max = 60) String name,
            @Size(max = 20) String code,
            Integer ordinal,
            List<@Valid SectionSeed> sections
    ) {
    }

    public record SectionSeed(@NotBlank @Size(max = 60) String name, @Size(max = 20) String code, Integer capacity) {
    }

    public record FacultySeed(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 150) String name
    ) {
    }

    public record DepartmentSeed(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 40) String facultyCode
    ) {
    }

    public record ProgramSeed(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 40) String level,
            @Size(max = 40) String departmentCode,
            Integer durationSemesters
    ) {
    }

    /** Step 7 — initial administrator. */
    public record AdministratorStep(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 3, max = 80) String loginId,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank String confirmPassword,
            @Size(max = 40) String phone
    ) {
    }

    /** Step 8 — optional modules. */
    public record ModulesStep(@NotEmpty Map<ModuleKey, Boolean> modules) {
    }

    /** Steps 9 + 10 — the full submission. */
    public record SetupRequest(
            @NotNull @Valid InstitutionStep institution,
            @NotNull @Valid AcademicStep academic,
            @Valid ContactStep contact,
            @Valid BrandingStep branding,
            @NotNull @Valid AcademicYearStep academicYear,
            @Valid AcademicStructureStep academicStructure,
            @NotNull @Valid AdministratorStep administrator,
            @Valid ModulesStep modules
    ) {
    }

    public record SetupResponse(
            boolean setupCompleted,
            String institutionCode,
            String adminLoginId,
            String adminEmail,
            String academicModel,
            List<String> createdModules,
            String message
    ) {
    }

    /** Non-secret metadata describing what the wizard will create. */
    public record SetupPreview(
            boolean setupRequired,
            String institutionName,
            String academicModel,
            boolean classesApplicable,
            boolean programsApplicable,
            List<String> defaultModules
    ) {
    }
}
