package com.phenikaa.cse702051.medbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;

@Repository
public class MedicalRecordRepository {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;

    public MedicalRecordRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public Optional<MedicalRecord> findById(Long id) {
        return queryOne("""
                SELECT mr.*, p.user_id, p.patient_code, p.full_name
                FROM medical_records mr
                JOIN patients p ON p.id = mr.patient_id
                WHERE mr.id = :id
                """, Map.of("id", id));
    }

    public Optional<MedicalRecord> findByPatientId(Long patientId) {
        return queryOne("""
                SELECT mr.*, p.user_id, p.patient_code, p.full_name
                FROM medical_records mr
                JOIN patients p ON p.id = mr.patient_id
                WHERE mr.patient_id = :patientId
                """, Map.of("patientId", patientId));
    }

    private Optional<MedicalRecord> queryOne(String sql, Map<String, ?> parameters) {
        return jdbc().query(sql, parameters, this::mapMedicalRecord).stream().findFirst();
    }

    private MedicalRecord mapMedicalRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        User user = null;
        Long userId = nullableLong(resultSet, "user_id");
        if (userId != null) {
            user = new User();
            user.setId(userId);
        }

        Patient patient = new Patient();
        patient.setId(resultSet.getLong("patient_id"));
        patient.setUser(user);
        patient.setPatientCode(resultSet.getString("patient_code"));
        patient.setFullName(resultSet.getString("full_name"));

        return MedicalRecord.builder()
                .id(resultSet.getLong("id"))
                .patient(patient)
                .recordCode(resultSet.getString("record_code"))
                .bloodType(resultSet.getString("blood_type"))
                .chronicConditions(resultSet.getString("chronic_conditions"))
                .allergyNotes(resultSet.getString("allergy_notes"))
                .medicalHistory(resultSet.getString("medical_history"))
                .currentMedications(resultSet.getString("current_medications"))
                .status(resultSet.getString("status"))
                .createdAt(resultSet.getObject("created_at", LocalDateTime.class))
                .updatedAt(resultSet.getObject("updated_at", LocalDateTime.class))
                .build();
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        Number value = (Number) resultSet.getObject(columnName);
        return value == null ? null : value.longValue();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Database access is not configured");
        }
        return jdbcTemplate;
    }
}
