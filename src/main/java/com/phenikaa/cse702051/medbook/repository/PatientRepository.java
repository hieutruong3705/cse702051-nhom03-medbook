package com.phenikaa.cse702051.medbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;

@Repository
public class PatientRepository {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;

    public PatientRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public Optional<Patient> findById(Long id) {
        return queryOne("""
                SELECT *
                FROM patients
                WHERE id = :id
                """, Map.of("id", id));
    }

    public Optional<Patient> findByUserId(Long userId) {
        return queryOne("""
                SELECT *
                FROM patients
                WHERE user_id = :userId
                """, Map.of("userId", userId));
    }

    public Optional<Patient> findByPatientCode(String patientCode) {
        return queryOne("""
                SELECT *
                FROM patients
                WHERE patient_code = :patientCode
                """, Map.of("patientCode", patientCode));
    }

    public Patient save(Patient patient) {
        if (patient.getId() == null) {
            return insert(patient);
        }
        return update(patient);
    }

    private Patient insert(Patient patient) {
        LocalDateTime now = LocalDateTime.now();
        patient.setCreatedAt(patient.getCreatedAt() == null ? now : patient.getCreatedAt());
        patient.setUpdatedAt(patient.getUpdatedAt() == null ? now : patient.getUpdatedAt());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc().update("""
                INSERT INTO patients (
                    user_id, patient_code, full_name, date_of_birth, gender_code,
                    phone, email, address, emergency_contact_name,
                    emergency_contact_phone, blood_type, allergies, status,
                    created_at, updated_at
                )
                VALUES (
                    :userId, :patientCode, :fullName, :dateOfBirth, :genderCode,
                    :phone, :email, :address, :emergencyContactName,
                    :emergencyContactPhone, :bloodType, :allergies, :status,
                    :createdAt, :updatedAt
                )
                """, toParameters(patient), keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            patient.setId(key.longValue());
        }
        return patient;
    }

    private Patient update(Patient patient) {
        patient.setUpdatedAt(LocalDateTime.now());
        jdbc().update("""
                UPDATE patients
                SET full_name = :fullName,
                    date_of_birth = :dateOfBirth,
                    gender_code = :genderCode,
                    phone = :phone,
                    email = :email,
                    address = :address,
                    emergency_contact_name = :emergencyContactName,
                    emergency_contact_phone = :emergencyContactPhone,
                    blood_type = :bloodType,
                    allergies = :allergies,
                    status = :status,
                    updated_at = :updatedAt
                WHERE id = :id
                """, toParameters(patient));
        return patient;
    }

    private Optional<Patient> queryOne(String sql, Map<String, ?> parameters) {
        return jdbc().query(sql, parameters, this::mapPatient).stream().findFirst();
    }

    private MapSqlParameterSource toParameters(Patient patient) {
        return new MapSqlParameterSource()
                .addValue("id", patient.getId())
                .addValue("userId", patient.getUser() == null ? null : patient.getUser().getId())
                .addValue("patientCode", patient.getPatientCode())
                .addValue("fullName", patient.getFullName())
                .addValue("dateOfBirth", patient.getDateOfBirth())
                .addValue("genderCode", patient.getGenderCode())
                .addValue("phone", patient.getPhone())
                .addValue("email", patient.getEmail())
                .addValue("address", patient.getAddress())
                .addValue("emergencyContactName", patient.getEmergencyContactName())
                .addValue("emergencyContactPhone", patient.getEmergencyContactPhone())
                .addValue("bloodType", patient.getBloodType())
                .addValue("allergies", patient.getAllergies())
                .addValue("status", patient.getStatus())
                .addValue("createdAt", patient.getCreatedAt())
                .addValue("updatedAt", patient.getUpdatedAt());
    }

    private Patient mapPatient(ResultSet resultSet, int rowNumber) throws SQLException {
        User user = null;
        Long userId = nullableLong(resultSet, "user_id");
        if (userId != null) {
            user = new User();
            user.setId(userId);
        }

        return Patient.builder()
                .id(resultSet.getLong("id"))
                .user(user)
                .patientCode(resultSet.getString("patient_code"))
                .fullName(resultSet.getString("full_name"))
                .dateOfBirth(resultSet.getObject("date_of_birth", java.time.LocalDate.class))
                .genderCode(resultSet.getString("gender_code"))
                .phone(resultSet.getString("phone"))
                .email(resultSet.getString("email"))
                .address(resultSet.getString("address"))
                .emergencyContactName(resultSet.getString("emergency_contact_name"))
                .emergencyContactPhone(resultSet.getString("emergency_contact_phone"))
                .bloodType(resultSet.getString("blood_type"))
                .allergies(resultSet.getString("allergies"))
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
