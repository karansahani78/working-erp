package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentNumberSettingRepository extends JpaRepository<StudentNumberSetting, java.util.UUID> {

    Optional<StudentNumberSetting> findFirstByActiveTrue();
}