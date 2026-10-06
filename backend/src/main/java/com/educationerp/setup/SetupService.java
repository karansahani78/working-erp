package com.educationerp.setup;

import com.educationerp.academic.AcademicYear;
import com.educationerp.academic.AcademicYearRepository;
import com.educationerp.academic.Department;
import com.educationerp.academic.DepartmentRepository;
import com.educationerp.academic.Faculty;
import com.educationerp.academic.FacultyRepository;
import com.educationerp.academic.Program;
import com.educationerp.academic.ProgramRepository;
import com.educationerp.academic.SchoolClass;
import com.educationerp.academic.SchoolClassRepository;
import com.educationerp.academic.Section;
import com.educationerp.academic.SectionRepository;
import com.educationerp.auth.permission.Permission;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.role.RoleDefaults;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.Enums;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.institution.AcademicModel;
import com.educationerp.institution.Institution;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.institution.ModuleSetting;
import com.educationerp.institution.ModuleSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * First-run setup.
 *
 * The whole wizard is one transaction: if any step fails the installation stays empty and
 * the wizard can be retried. Re-running the wizard after completion is refused.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    private final InstitutionService institutionService;
    private final ModuleSettingRepository moduleSettingRepository;
    private final UserRepository userRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;

    /**
     * Describes what the wizard will create, so the frontend can render the right steps
     * for the chosen academic model instead of guessing.
     */
    @Transactional(readOnly = true)
    public SetupDtos.SetupPreview preview() {
        Institution existing = institutionService.findInstitutionUncached().orElse(null);
        boolean setupRequired = existing == null || !existing.isSetupCompleted();
        AcademicModel model = existing == null ? null : existing.getAcademicModel();
        return new SetupDtos.SetupPreview(
                setupRequired,
                existing == null ? null : existing.getName(),
                model == null ? null : model.name(),
                model == null || model.usesClassSectionStructure(),
                model == null || model.usesFacultyDepartmentProgram(),
                enabledDefaults());
    }

    @Transactional
    public SetupDtos.SetupResponse complete(SetupDtos.SetupRequest request) {
        boolean alreadyConfigured = institutionService.findInstitutionUncached()
                .map(Institution::isSetupCompleted)
                .orElse(false);
        if (alreadyConfigured) {
            throw new AppException(ErrorCode.SETUP_ALREADY_COMPLETED);
        }
        if (institutionService.findInstitutionUncached().isPresent()) {
            throw new AppException(ErrorCode.SETUP_ALREADY_COMPLETED,
                    "An institution record already exists. Complete the existing setup instead.");
        }

        validateAcademicYear(request);

        Institution institution = new Institution();
        institution.setName(request.institution().name().trim());
        institution.setShortName(request.institution().shortName().trim());
        institution.setInstitutionCode(request.institution().institutionCode().trim().toUpperCase(Locale.ROOT));
        institution.setInstitutionType(request.institution().institutionType());
        institution.setAcademicModel(request.academic().academicModel());
        applyContact(institution, request.contact());
        applyBranding(institution, request.branding());
        applyCalendarDefaults(institution, request.academicYear());
        institution.setSetupCompleted(true);
        Institution savedInstitution = institutionService.save(institution);

        List<String> enabledModules = configureModules(request.modules());

        AcademicYear academicYear = createAcademicYear(request);
        AcademicModel model = request.academic().academicModel();
        SetupDtos.AcademicStructureStep structure = request.academicStructure();
        int createdClasses = 0;
        int createdPrograms = 0;
        if (model.usesClassSectionStructure() && structure != null && structure.classes() != null) {
            createdClasses = createClasses(structure, academicYear);
        }
        if (model.usesFacultyDepartmentProgram() && structure != null) {
            createdPrograms = createPrograms(structure);
        }

        seedRoles();
        User admin = createAdministrator(request.administrator());

        auditService.record(AuditEvent.builder()
                .action(AuditAction.SETUP)
                .entityType("Institution")
                .entityId(savedInstitution.getId().toString())
                .entityLabel(savedInstitution.getName())
                .module("setup")
                .summary("Initial setup completed for " + savedInstitution.getName()
                        + " (" + model + "); academic year " + academicYear.getName()
                        + "; " + createdClasses + " class(es), " + createdPrograms + " program(s)")
                .after(Map.of(
                        "institutionCode", savedInstitution.getInstitutionCode(),
                        "academicModel", model.name(),
                        "modules", enabledModules))
                .build());

        log.info("Setup completed: institution={} admin={} modules={}",
                savedInstitution.getInstitutionCode(), admin.getUsername(), enabledModules);

        return new SetupDtos.SetupResponse(
                true,
                savedInstitution.getInstitutionCode(),
                admin.getUsername(),
                admin.getEmail(),
                model.name(),
                enabledModules,
                "Setup complete. You can now sign in with the administrator account.");
    }

    private void validateAcademicYear(SetupDtos.SetupRequest request) {
        var year = request.academicYear();
        if (year.endDate().isBefore(year.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The academic year end date must fall after its start date.",
                    Map.of("academicYear.endDate", "End date must be after the start date."));
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(year.startDate(), year.endDate()) > 3660) {
            throw AppException.rule("The academic year dates look incorrect.");
        }
    }

    private void applyContact(Institution institution, SetupDtos.ContactStep contact) {
        if (contact == null) {
            return;
        }
        institution.setAddress(contact.address());
        institution.setMunicipality(contact.municipality());
        institution.setDistrict(contact.district());
        institution.setProvince(contact.province());
        if (contact.country() != null && !contact.country().isBlank()) {
            institution.setCountry(contact.country().trim());
        }
        institution.setPhone(contact.phone());
        institution.setEmail(contact.email());
        institution.setWebsite(contact.website());
        institution.setSupportEmail(contact.supportEmail());
        institution.setSupportPhone(contact.supportPhone());
        institution.setPortalTitle(contact.portalTitle() == null || contact.portalTitle().isBlank()
                ? "Student Portal" : contact.portalTitle().trim());
        institution.setPortalDescription(contact.portalDescription());
    }

    private void applyBranding(Institution institution, SetupDtos.BrandingStep branding) {
        if (branding == null) {
            return;
        }
        if (branding.logoUrl() != null && !branding.logoUrl().isBlank()) {
            institution.setLogoUrl(branding.logoUrl().trim());
        }
        if (branding.faviconUrl() != null && !branding.faviconUrl().isBlank()) {
            institution.setFaviconUrl(branding.faviconUrl().trim());
        }
        if (branding.primaryColor() != null && !branding.primaryColor().isBlank()) {
            institution.setPrimaryColor(branding.primaryColor().toUpperCase(Locale.ROOT));
        }
        if (branding.secondaryColor() != null && !branding.secondaryColor().isBlank()) {
            institution.setSecondaryColor(branding.secondaryColor().toUpperCase(Locale.ROOT));
        }
    }

    private void applyCalendarDefaults(Institution institution, SetupDtos.AcademicYearStep year) {
        institution.setFiscalYearStart(year.startDate());
        institution.setDateFormat(year.calendar() == null ? "AD" : year.calendar());
    }

    private List<String> configureModules(SetupDtos.ModulesStep modulesStep) {
        Map<ModuleKey, Boolean> requested = modulesStep == null || modulesStep.modules() == null
                ? defaultModuleMap()
                : modulesStep.modules();

        List<ModuleSetting> settings = new ArrayList<>();
        for (ModuleKey key : ModuleKey.values()) {
            boolean enabled = requested.getOrDefault(key, key.enabledByDefault());
            settings.add(ModuleSetting.of(key, enabled));
        }
        moduleSettingRepository.saveAll(settings);
        institutionService.invalidateCache();
        return settings.stream().filter(ModuleSetting::isEnabled).map(s -> s.getModuleKey().name()).toList();
    }

    private Map<ModuleKey, Boolean> defaultModuleMap() {
        Map<ModuleKey, Boolean> map = new EnumMap<>(ModuleKey.class);
        for (ModuleKey key : ModuleKey.values()) {
            map.put(key, key.enabledByDefault());
        }
        return map;
    }

    private List<String> enabledDefaults() {
        return java.util.Arrays.stream(ModuleKey.values())
                .filter(ModuleKey::enabledByDefault)
                .map(Enum::name)
                .toList();
    }

    private AcademicYear createAcademicYear(SetupDtos.SetupRequest request) {
        SetupDtos.AcademicYearStep seed = request.academicYear();
        AcademicYear year = new AcademicYear();
        year.setName(seed.name().trim());
        year.setCode(deriveYearCode(seed));
        year.setStartDate(seed.startDate());
        year.setEndDate(seed.endDate());
        year.setCalendar(Enums.parse(AcademicYear.CalendarType.class, seed.calendar(), "academicYear.calendar",
                AcademicYear.CalendarType.AD));
        year.setStatus(AcademicYear.Status.ACTIVE);
        year.setCurrent(true);
        return academicYearRepository.save(year);
    }

    private String deriveYearCode(SetupDtos.AcademicYearStep seed) {
        String normalised = seed.name().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!normalised.isEmpty()) {
            return normalised.length() > 40 ? normalised.substring(0, 40) : normalised;
        }
        return String.valueOf(seed.startDate().getYear());
    }

    private int createClasses(SetupDtos.AcademicStructureStep structure, AcademicYear academicYear) {
        int count = 0;
        Set<String> codes = new LinkedHashSet<>();
        int ordinal = 1;
        for (SetupDtos.ClassSeed classSeed : structure.classes()) {
            String code = classSeed.code() == null || classSeed.code().isBlank()
                    ? normaliseCode(classSeed.name()) : classSeed.code().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Duplicate class code: " + code,
                        Map.of("classes", "Class code '" + code + "' is used more than once."));
            }
            SchoolClass schoolClass = new SchoolClass();
            schoolClass.setAcademicYear(academicYear);
            schoolClass.setName(classSeed.name().trim());
            schoolClass.setCode(code);
            schoolClass.setOrdinal(classSeed.ordinal() == null ? ordinal++ : classSeed.ordinal());
            schoolClass.setCapacity(0);
            SchoolClass saved = classRepository.save(schoolClass);

            if (classSeed.sections() != null) {
                Set<String> sectionCodes = new LinkedHashSet<>();
                for (SetupDtos.SectionSeed sectionSeed : classSeed.sections()) {
                    String sectionCode = sectionSeed.code() == null || sectionSeed.code().isBlank()
                            ? normaliseCode(sectionSeed.name()) : sectionSeed.code().trim().toUpperCase(Locale.ROOT);
                    if (!sectionCodes.add(sectionCode)) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Duplicate section code: " + sectionCode);
                    }
                    Section section = new Section();
                    section.setSchoolClass(saved);
                    section.setName(sectionSeed.name().trim());
                    section.setCode(sectionCode);
                    section.setCapacity(sectionSeed.capacity());
                    sectionRepository.save(section);
                }
            }
            count++;
        }
        return count;
    }

    private int createPrograms(SetupDtos.AcademicStructureStep structure) {
        Map<String, Faculty> faculties = new HashMap<>();
        if (structure.faculties() != null) {
            for (SetupDtos.FacultySeed seed : structure.faculties()) {
                String code = seed.code().trim().toUpperCase(Locale.ROOT);
                Faculty faculty = facultyRepository.findByCodeIgnoreCase(code).orElseGet(Faculty::new);
                faculty.setCode(code);
                faculty.setName(seed.name().trim());
                faculties.put(code, facultyRepository.save(faculty));
            }
        }

        Map<String, Department> departments = new HashMap<>();
        if (structure.departments() != null) {
            for (SetupDtos.DepartmentSeed seed : structure.departments()) {
                String code = seed.code().trim().toUpperCase(Locale.ROOT);
                Department department = departmentRepository.findByCodeIgnoreCase(code).orElseGet(Department::new);
                department.setCode(code);
                department.setName(seed.name().trim());
                if (seed.facultyCode() != null && !seed.facultyCode().isBlank()) {
                    Faculty faculty = faculties.get(seed.facultyCode().trim().toUpperCase(Locale.ROOT));
                    if (faculty == null) {
                        faculty = facultyRepository.findByCodeIgnoreCase(seed.facultyCode()).orElse(null);
                    }
                    if (faculty == null) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Faculty '" + seed.facultyCode() + "' was not defined.");
                    }
                    department.setFaculty(faculty);
                }
                departments.put(code, departmentRepository.save(department));
            }
        }

        int count = 0;
        if (structure.programs() != null) {
            for (SetupDtos.ProgramSeed seed : structure.programs()) {
                String code = seed.code().trim().toUpperCase(Locale.ROOT);
                Program program = programRepository.findByCodeIgnoreCase(code).orElseGet(Program::new);
                program.setCode(code);
                program.setName(seed.name().trim());
                program.setLevel(seed.level());
                program.setDurationSemesters(seed.durationSemesters());
                if (seed.departmentCode() != null && !seed.departmentCode().isBlank()) {
                    Department department = departments.get(seed.departmentCode().trim().toUpperCase(Locale.ROOT));
                    if (department == null) {
                        department = departmentRepository.findByCodeIgnoreCase(seed.departmentCode()).orElse(null);
                    }
                    if (department == null) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Department '" + seed.departmentCode() + "' was not defined.");
                    }
                    program.setDepartment(department);
                }
                program.setStatus(Program.Status.ACTIVE);
                programRepository.save(program);
                count++;
            }
        }
        return count;
    }

    private void seedRoles() {
        for (Role role : Role.values()) {
            Set<String> permissions = RoleDefaults.forRole(role).stream()
                    .map(Permission::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            RoleDefinition definition = roleDefinitionRepository.findByCode(role)
                    .orElseGet(() -> RoleDefinition.builtin(role, humanise(role.name()),
                            role.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " role", permissions));
            definition.setName(humanise(role.name()));
            if (definition.getPermissions().isEmpty()) {
                definition.setPermissions(permissions);
            }
            roleDefinitionRepository.save(definition);
        }
    }

    private User createAdministrator(SetupDtos.AdministratorStep admin) {
        if (!admin.password().equals(admin.confirmPassword())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "The passwords do not match.",
                    Map.of("confirmPassword", "Passwords do not match."));
        }
        if (userRepository.existsByUsernameIgnoreCase(admin.loginId())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "That login ID is already taken.",
                    Map.of("loginId", "This login ID is already in use."));
        }
        if (admin.email() != null && userRepository.existsByEmailIgnoreCase(admin.email())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "That email address is already registered.",
                    Map.of("email", "This email address is already in use."));
        }
        User user = new User();
        user.setUsername(admin.loginId().trim());
        user.setEmail(admin.email().trim().toLowerCase(Locale.ROOT));
        user.setPhone(admin.phone());
        user.setDisplayName(admin.fullName().trim());
        user.setPrimaryRole(Role.SUPER_ADMIN);
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setPasswordChangedAt(Instant.now());
        user.setEmailVerified(true);
        user.setMustChangePassword(false);
        userRepository.save(user);
        return user;
    }

    private String normaliseCode(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");
        if (code.length() > 20) {
            code = code.substring(0, 20);
        }
        return code.isEmpty() ? "X" : code;
    }

    private String humanise(String enumName) {
        String lower = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
