package com.phenikaa.cse702051.medbook.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class DataMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    public DataMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Đếm số bảng nghiệp vụ hiện có trong database.
     */
    public int countTables() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """,
                Integer.class
        );

        return count != null ? count : 0;
    }

    /**
     * Đếm tổng số index trong database.
     */
    public int countIndexes() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT CONCAT(
                    table_name, ':', index_name
                ))
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                """,
                Integer.class
        );

        return count != null ? count : 0;
    }

    /**
     * Đếm tổng số foreign key constraint.
     */
    public int countForeignKeys() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'FOREIGN KEY'
                """,
                Integer.class
        );

        return count != null ? count : 0;
    }

    /**
     * Đo median thời gian thực thi của hot query.
     *
     * Query được thực hiện 50 lần với tham số hóa.
     * Thời gian đo là thời gian thực thi SQL tại tầng Data Access,
     * không phải thời gian HTTP/API.
     */
    public double measureHotQueryMedianMs() {

        final String sql = """
                SELECT COUNT(*)
                FROM appointments
                WHERE patient_id = ?
                  AND appointment_date = ?
                """;

        final Long patientId = 1L;
        final LocalDate appointmentDate = LocalDate.now();

        List<Double> executionTimes = new ArrayList<>();

        // Warm-up
        for (int i = 0; i < 5; i++) {
            jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    patientId,
                    appointmentDate
            );
        }

        // Đo 50 lần
        for (int i = 0; i < 50; i++) {

            long start = System.nanoTime();

            jdbcTemplate.queryForObject(
                    sql,
                    Integer.class,
                    patientId,
                    appointmentDate
            );

            long end = System.nanoTime();

            double elapsedMs =
                    (end - start) / 1_000_000.0;

            executionTimes.add(elapsedMs);
        }

        Collections.sort(executionTimes);

        int size = executionTimes.size();

        if (size % 2 == 0) {
            return (
                    executionTimes.get(size / 2 - 1)
                    + executionTimes.get(size / 2)
            ) / 2.0;
        }

        return executionTimes.get(size / 2);
    }
}