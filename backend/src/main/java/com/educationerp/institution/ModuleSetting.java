package com.educationerp.institution;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Per-module on/off switch. Only modules that are both implemented and enabled here are
 * reachable through navigation, the API and dashboards.
 */
@Entity
@Table(name = "module_settings")
public class ModuleSetting extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "module_key", nullable = false, unique = true, length = 40)
    private ModuleKey moduleKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * Free-form per-module configuration stored as JSON. Written as a Hibernate type so
     * the value reaches the jsonb column as jsonb rather than as text, which PostgreSQL
     * rejects.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings")
    private String settings;

    public ModuleSetting() {
    }

    public static ModuleSetting of(ModuleKey key, boolean enabled) {
        ModuleSetting setting = new ModuleSetting();
        setting.moduleKey = key;
        setting.enabled = enabled;
        return setting;
    }

    public ModuleKey getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(ModuleKey moduleKey) {
        this.moduleKey = moduleKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSettings() {
        return settings;
    }

    public void setSettings(String settings) {
        this.settings = settings;
    }
}
