package com.educationerp.support;

import com.educationerp.academic.AcademicYear;
import com.educationerp.academic.AcademicYearRepository;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.auth.user.UserStatus;
import com.educationerp.institution.AcademicModel;
import com.educationerp.institution.Institution;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.InstitutionType;
import com.educationerp.institution.ModuleKey;
import com.educationerp.institution.ModuleSetting;
import com.educationerp.institution.ModuleSettingRepository;
import com.educationerp.setup.SetupDtos;
import com.educationerp.setup.SetupService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Builds the minimum viable world for a test by running the real first-run wizard, so
 * the institution, roles, academic year and administrator exist exactly as they do in
 * production. Additional users are created through the user repository.
 */
@Component
public class TestData {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "TestPass123";
    public static final LocalDate YEAR_START = LocalDate.of(2023, 4, 1);
    public static final LocalDate YEAR_END = LocalDate.of(2024, 3, 31);

    private final SetupService setupService;
    private final InstitutionService institutionService;
    private final UserRepository userRepository;
    private final AcademicYearRepository academicYearRepository;
    private final ModuleSettingRepository moduleSettings;
    private final PasswordEncoder passwordEncoder;

    private Institution institution;
    private User admin;

    public TestData(SetupService setupService,
                    InstitutionService institutionService,
                    UserRepository userRepository,
                    AcademicYearRepository academicYearRepository,
                    ModuleSettingRepository moduleSettings,
                    PasswordEncoder passwordEncoder) {
        this.setupService = setupService;
        this.institutionService = institutionService;
        this.userRepository = userRepository;
        this.academicYearRepository = academicYearRepository;
        this.moduleSettings = moduleSettings;
        this.passwordEncoder = passwordEncoder;
    }

    public void reset() {
        institution = null;
        admin = null;
    }

    /** Runs the setup wizard once per test, returning the created institution. */
    public Institution institution() {
        if (institution == null) {
            setupService.complete(new SetupDtos.SetupRequest(
                    new SetupDtos.InstitutionStep("Sunrise Academy", "Sunrise", "SUNRISE", InstitutionType.SCHOOL),
                    new SetupDtos.AcademicStep(AcademicModel.SCHOOL),
                    new SetupDtos.ContactStep("1 Main Road", "Kathmandu", "Kathmandu", "Bagmati",
                            "Nepal", "+977-1-5555555", "info@sunrise.edu.np", "https://sunrise.edu.np",
                            "support@sunrise.edu.np", "+977-1-5555556", "Sunrise Portal", "School portal"),
                    new SetupDtos.BrandingStep(null, null, "#0F5132", "#EAE5DB"),
                    academicYearStep(),
                    structure(),
                    new SetupDtos.AdministratorStep("Site Administrator", "admin@sunrise.edu.np",
                            ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_PASSWORD, "+977-1-5555557"),
                    new SetupDtos.ModulesStep(Map.of())));
            institution = institutionService.requireInstitution();
            admin = userRepository.findByUsernameIgnoreCase(ADMIN_USERNAME).orElseThrow();
        }
        return institution;
    }

    public SetupDtos.AcademicYearStep academicYearStep() {
        return new SetupDtos.AcademicYearStep("2080/81", YEAR_START, YEAR_END, "AD");
    }

    public SetupDtos.AcademicStructureStep structure() {
        return new SetupDtos.AcademicStructureStep(
                List.of(
                        new SetupDtos.ClassSeed("Grade 10", "G10", 10, List.of(
                                new SetupDtos.SectionSeed("A", "A", 40),
                                new SetupDtos.SectionSeed("B", "B", 40))),
                        new SetupDtos.ClassSeed("Grade 11", "G11", 11, List.of(
                                new SetupDtos.SectionSeed("A", "A", 40))),
                        new SetupDtos.ClassSeed("Grade 12", "G12", 12, List.of())),
                List.of(),
                List.of(),
                List.of());
    }

    public User admin() {
        institution();
        return admin;
    }

    public User user(String username, String displayName, String email, Role role, String rawPassword) {
        return userRepository.save(userEntity(username, displayName, email, role, rawPassword));
    }

    private User userEntity(String username, String displayName, String email, Role role, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPrimaryRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(java.time.Instant.now());
        user.setEmailVerified(true);
        return user;
    }

    /**
     * Switches a module on for a test. Modules such as HR and payroll ship disabled, so a
     * test that needs one says so explicitly rather than relying on the seeded defaults.
     */
    public void enableModule(ModuleKey key) {
        institution();
        ModuleSetting setting = moduleSettings.findByModuleKey(key).orElseGet(() -> {
            ModuleSetting created = ModuleSetting.of(key, true);
            return moduleSettings.save(created);
        });
        setting.setEnabled(true);
        moduleSettings.save(setting);
    }

    public AcademicYear academicYear() {
        institution();
        return academicYearRepository.findFirstByCurrentTrue()
                .orElseThrow(() -> new IllegalStateException("No current academic year was created"));
    }

    /**
     * Creates an additional academic year. Only one year may be flagged current, so this
     * helper leaves {@code current} false unless a test explicitly needs otherwise.
     */
    public AcademicYear academicYear(String name, String code, LocalDate start, LocalDate end) {
        AcademicYear year = new AcademicYear();
        year.setName(name);
        year.setCode(code);
        year.setStartDate(start);
        year.setEndDate(end);
        year.setCalendar(AcademicYear.CalendarType.AD);
        year.setStatus(AcademicYear.Status.PLANNED);
        year.setCurrent(false);
        return academicYearRepository.save(year);
    }
}