package com.phenikaa.cse702051.medbook.repository;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EncounterRepository {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;

    public EncounterRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public boolean existsByMedicalRecordIdAndDoctorId(Long medicalRecordId, Long doctorId) {
        Integer count = jdbc().queryForObject("""
                SELECT COUNT(*)
                FROM encounters
                WHERE medical_record_id = :medicalRecordId
                  AND doctor_id = :doctorId
                """, Map.of("medicalRecordId", medicalRecordId, "doctorId", doctorId), Integer.class);
        return count != null && count > 0;
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Database access is not configured");
        }
        return jdbcTemplate;
    }
}
