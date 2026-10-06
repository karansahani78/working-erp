package com.educationerp.institution;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * The single institution configured for this deployment. Root configuration object —
 * everything white-label (name, logo, colours, contact, academic model) lives here.
 */
@Entity
@Table(name = "institution")
public class Institution extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "short_name", nullable = false, length = 40)
    private String shortName;

    @Column(name = "institution_code", nullable = false, unique = true, length = 40)
    private String institutionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", nullable = false, length = 40)
    private InstitutionType institutionType;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "primary_color", nullable = false, length = 20)
    private String primaryColor = "#0F5132";

    @Column(name = "secondary_color", nullable = false, length = 20)
    private String secondaryColor = "#EAE5DB";

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "municipality", length = 120)
    private String municipality;

    @Column(name = "district", length = 120)
    private String district;

    @Column(name = "province", length = 120)
    private String province;

    @Column(name = "country", nullable = false, length = 80)
    private String country = "Nepal";

    @Column(name = "phone", length = 60)
    private String phone;

    @Column(name = "email", length = 180)
    private String email;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "timezone", nullable = false, length = 80)
    private String timezone = "Asia/Kathmandu";

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "NPR";

    @Column(name = "fiscal_year_start", nullable = false)
    private LocalDate fiscalYearStart;

    @Column(name = "date_format", nullable = false, length = 20)
    private String dateFormat = "AD";

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_model", nullable = false, length = 20)
    private AcademicModel academicModel;

    @Column(name = "portal_title", length = 120)
    private String portalTitle;

    @Column(name = "portal_description", length = 400)
    private String portalDescription;

    @Column(name = "support_email", length = 180)
    private String supportEmail;

    @Column(name = "support_phone", length = 60)
    private String supportPhone;

    @Column(name = "setup_completed", nullable = false)
    private boolean setupCompleted = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getInstitutionCode() {
        return institutionCode;
    }

    public void setInstitutionCode(String institutionCode) {
        this.institutionCode = institutionCode;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public void setInstitutionType(InstitutionType institutionType) {
        this.institutionType = institutionType;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getMunicipality() {
        return municipality;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getFiscalYearStart() {
        return fiscalYearStart;
    }

    public void setFiscalYearStart(LocalDate fiscalYearStart) {
        this.fiscalYearStart = fiscalYearStart;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public AcademicModel getAcademicModel() {
        return academicModel;
    }

    public void setAcademicModel(AcademicModel academicModel) {
        this.academicModel = academicModel;
    }

    public String getPortalTitle() {
        return portalTitle;
    }

    public void setPortalTitle(String portalTitle) {
        this.portalTitle = portalTitle;
    }

    public String getPortalDescription() {
        return portalDescription;
    }

    public void setPortalDescription(String portalDescription) {
        this.portalDescription = portalDescription;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    public String getSupportPhone() {
        return supportPhone;
    }

    public void setSupportPhone(String supportPhone) {
        this.supportPhone = supportPhone;
    }

    public boolean isSetupCompleted() {
        return setupCompleted;
    }

    public void setSetupCompleted(boolean setupCompleted) {
        this.setupCompleted = setupCompleted;
    }
}
