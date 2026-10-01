package com.phenikaa.cse702051.medbook.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;

@Repository
public class MedicalRecordRepository {

    // Khởi tạo dữ liệu mẫu khớp 100% với file seed.sql
    private final Map<Long, MedicalRecordDTO> database = new ConcurrentHashMap<>();

    public MedicalRecordRepository() {
        database.put(1L, new MedicalRecordDTO(
                1L,
                "MR0001",
                1L,
                "Nguyễn Minh An",
                "O+",
                "Không ghi nhận bệnh mạn tính trong dữ liệu demo.",
                "Không ghi nhận dị ứng.",
                "Tiền sử giả lập: không có thông tin đặc biệt.",
                "Không sử dụng thuốc thường xuyên.",
                "ACTIVE"));

        database.put(2L, new MedicalRecordDTO(
                2L,
                "MR0002",
                2L,
                "Trần Gia Bình",
                "A+",
                "Viêm xoang dị ứng thời tiết.",
                "Dị ứng penicillin.",
                "Tiền sử giả lập: điều trị viêm amidan năm 2022.",
                "Kháng histamin khi cần.",
                "ACTIVE"));

        database.put(3L, new MedicalRecordDTO(
                3L,
                "MR0003",
                3L,
                "Lê Ngọc Chi",
                "B+",
                "Không ghi nhận bệnh lý nền.",
                "Không có dị ứng thức ăn hoặc thuốc.",
                "Tiền sử khỏe mạnh.",
                "Không có.",
                "ACTIVE"));
    }

    public Optional<MedicalRecordDTO> findById(Long id) {
        return Optional.ofNullable(database.get(id));
    }

    public Optional<MedicalRecordDTO> findByPatientId(Long patientId) {
        return database.values().stream()
                .filter(r -> r.patientId().equals(patientId))
                .findFirst();
    }
}
