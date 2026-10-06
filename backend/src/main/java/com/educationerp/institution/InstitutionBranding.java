package com.educationerp.institution;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Safe, non-sensitive branding payload consumed by the login page, portals and document
 * headers. Nothing secret is ever included.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstitutionBranding(
        String name,
        String shortName,
        String institutionCode,
        @JsonProperty("institutionType") String institutionType,
        String logoUrl,
        String faviconUrl,
        String primaryColor,
        String secondaryColor,
        @JsonProperty("academicModel") String academicModel,
        String portalTitle,
        String portalDescription,
        String supportEmail,
        String supportPhone,
        String phone,
        String email,
        String website,
        String address,
        String municipality,
        String district,
        String province,
        String country,
        String timezone,
        String currency,
        String dateFormat,
        @JsonProperty("setupCompleted") boolean setupCompleted
) {
}
