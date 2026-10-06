package com.educationerp.institution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ModuleSettingRepository extends JpaRepository<ModuleSetting, UUID> {

    Optional<ModuleSetting> findByModuleKey(ModuleKey moduleKey);

    List<ModuleSetting> findAllByOrderByModuleKeyAsc();

    default Map<ModuleKey, Boolean> asMap() {
        Map<ModuleKey, Boolean> map = new EnumMap<>(ModuleKey.class);
        for (ModuleSetting setting : findAllByOrderByModuleKeyAsc()) {
            map.put(setting.getModuleKey(), setting.isEnabled());
        }
        return map;
    }
}
