package com.phenikaa.cse702051.medbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.User;

@Repository
public class DoctorRepository {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;

    public DoctorRepository(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    public Optional<Doctor> findByUserId(Long userId) {
        return jdbc().query("""
                SELECT *
                FROM doctors
                WHERE user_id = :userId
                """, Map.of("userId", userId), this::mapDoctor).stream().findFirst();
    }

    private Doctor mapDoctor(ResultSet resultSet, int rowNumber) throws SQLException {
        User user = new User();
        user.setId(resultSet.getLong("user_id"));

        return Doctor.builder()
                .id(resultSet.getLong("id"))
                .user(user)
                .doctorCode(resultSet.getString("doctor_code"))
                .specialty(resultSet.getString("specialty"))
                .licenseNo(resultSet.getString("license_no"))
                .yearsExperience(resultSet.getInt("years_experience"))
                .bio(resultSet.getString("bio"))
                .status(resultSet.getString("status"))
                .createdAt(resultSet.getObject("created_at", LocalDateTime.class))
                .updatedAt(resultSet.getObject("updated_at", LocalDateTime.class))
                .build();
    }

    private NamedParameterJdbcTemplate jdbc() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Database access is not configured");
        }
        return jdbcTemplate;
    }
}
