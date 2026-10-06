package com.educationerp.reporting;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the report catalogue together once, at startup.
 *
 * <p>The definitions come from one component and are indexed here, so a report added to
 * {@link ReportDefinitions} appears in the catalogue without being registered anywhere else.
 * Two reports sharing a key is a mistake worth failing at startup over rather than at the moment
 * somebody asks for one of them.
 */
@Configuration
public class ReportingConfig {

    @Bean
    public ReportRegistry reportRegistry(ReportDefinitions definitions) {
        return new ReportRegistry(definitions.definitions());
    }
}
