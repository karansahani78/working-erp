package com.educationerp.institution;

import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Single source of truth for the institution configuration.
 *
 * Configuration is cached for a short window and invalidated on write, so a branding
 * change takes effect without an application restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private static final String CACHE_KEY = "institution";
    private static final Cache<String, Optional<Institution>> BRANDING_CACHE = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    private final InstitutionRepository institutionRepository;
    private final ModuleSettingRepository moduleSettingRepository;

    @Transactional(readOnly = true)
    public Optional<Institution> findInstitution() {
        return BRANDING_CACHE.get(CACHE_KEY, key -> institutionRepository.findFirstByOrderByCreatedAtAsc());
    }

    @Transactional(readOnly = true)
    public Optional<Institution> findInstitutionUncached() {
        return institutionRepository.findFirstByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public Institution requireInstitution() {
        return findInstitution().orElseThrow(() -> new AppException(ErrorCode.SETUP_NOT_AVAILABLE));
    }

    public boolean isSetupCompleted() {
        return findInstitution().map(Institution::isSetupCompleted).orElse(false);
    }

    public AcademicModel academicModel() {
        return findInstitution().map(Institution::getAcademicModel).orElse(null);
    }

    public String currency() {
        return findInstitution().map(Institution::getCurrency).orElse("NPR");
    }

    @Transactional(readOnly = true)
    public InstitutionBranding branding() {
        return findInstitution().map(this::toBranding).orElse(null);
    }

    @Transactional
    public Institution save(Institution institution) {
        Institution saved = institutionRepository.save(institution);
        invalidateCache();
        log.info("Institution configuration saved: id={} code={}", saved.getId(), saved.getInstitutionCode());
        return saved;
    }

    /**
     * Feature-flag guard. Disabled modules are rejected at the API boundary, not just
     * hidden in the UI.
     */
    @Transactional(readOnly = true)
    public void requireModuleEnabled(ModuleKey key) {
        if (key == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A module must be specified.");
        }
        if (!isModuleEnabled(key)) {
            throw new AppException(ErrorCode.MODULE_DISABLED);
        }
    }

    @Transactional(readOnly = true)
    public boolean isModuleEnabled(ModuleKey key) {
        return moduleSettingRepository.findByModuleKey(key)
                .map(ModuleSetting::isEnabled)
                .orElse(key.enabledByDefault());
    }

    public void invalidateCache() {
        BRANDING_CACHE.invalidateAll();
    }

    public InstitutionBranding toBranding(Institution institution) {
        return new InstitutionBranding(
                institution.getName(),
                institution.getShortName(),
                institution.getInstitutionCode(),
                institution.getInstitutionType() == null ? null : institution.getInstitutionType().name(),
                institution.getLogoUrl(),
                institution.getFaviconUrl(),
                institution.getPrimaryColor(),
                institution.getSecondaryColor(),
                institution.getAcademicModel() == null ? null : institution.getAcademicModel().name(),
                institution.getPortalTitle(),
                institution.getPortalDescription(),
                institution.getSupportEmail(),
                institution.getSupportPhone(),
                institution.getPhone(),
                institution.getEmail(),
                institution.getWebsite(),
                institution.getAddress(),
                institution.getMunicipality(),
                institution.getDistrict(),
                institution.getProvince(),
                institution.getCountry(),
                institution.getTimezone(),
                institution.getCurrency(),
                institution.getDateFormat(),
                institution.isSetupCompleted());
    }
}
