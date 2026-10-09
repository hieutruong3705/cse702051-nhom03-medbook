package com.phenikaa.cse702051.medbook.service;

import org.springframework.stereotype.Service;

import com.phenikaa.cse702051.medbook.dto.MedicalRecordDTO;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.repository.MedicalRecordRepository;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;

    public MedicalRecordService(MedicalRecordRepository medicalRecordRepository) {
        this.medicalRecordRepository = medicalRecordRepository;
    }

    /**
     * Lấy chi tiết hồ sơ bệnh án kèm theo kiểm soát phân quyền hai mức (Chống IDOR).
     *
     * @param id ID của hồ sơ bệnh án
     * @param authHeader Token xác thực Bearer từ client
     * @param simulatedPatientId ID bệnh nhân được mô phỏng hoặc giải mã từ Token
     * @param simulatedRole Vai trò người dùng (PATIENT, DOCTOR, ADMIN)
     * @return MedicalRecordDTO nếu hợp lệ
     */
    public MedicalRecordDTO getRecordForUser(
            Long id,
            String authHeader,
            Long simulatedPatientId,
            String simulatedRole) {

        // =========================================================================
        // MỨC 0: KIỂM TRA XÁC THỰC (AUTHENTICATION - LỖI 401 UNAUTHORIZED)
        // =========================================================================
        if ((authHeader == null || authHeader.isBlank()) && simulatedPatientId == null && simulatedRole == null) {
            throw new UnauthorizedException("Yêu cầu chưa được xác thực! Vui lòng cung cấp Bearer Token.");
        }

        // =========================================================================
        // TÌM KIẾM BẢN GHI (LỖI 404 NOT FOUND NẾU KHÔNG TỒN TẠI)
        // =========================================================================
        MedicalRecordDTO record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ bệnh án với mã ID: " + id));

        // Xác định danh tính người gọi:
        // Hỗ trợ cả Bearer Token theo tên user hoặc Header/Param giả lập trong Postman
        Long currentUserId = resolvePatientId(authHeader, simulatedPatientId);
        String currentRole = resolveRole(authHeader, simulatedRole);

        // =========================================================================
        // PHÂN QUYỀN MỨC 1: KIỂM QUYỀN VAI TRÒ CHỨC NĂNG (RBAC - LỖI 403 FORBIDDEN)
        // =========================================================================
        if ("ADMIN".equalsIgnoreCase(currentRole)) {
            // Theo quy định bảo vệ dữ liệu y tế: Quản trị viên IT không có quyền xem bệnh án chuyên môn
            throw new ForbiddenException("ACCESS_DENIED: Quản trị viên hệ thống không được phép xem nội dung chi tiết bệnh án!");
        }

        // =========================================================================
        // PHÂN QUYỀN MỨC 2: KIỂM QUYỀN TRÊN ĐỐI TƯỢNG (CHỐNG LỖ HỔNG IDOR - LỖI 403)
        // =========================================================================
        if ("PATIENT".equalsIgnoreCase(currentRole)) {
            // Bệnh nhân CHỈ ĐƯỢC XEM bệnh án của chính mình
            if (currentUserId == null || !record.patientId().equals(currentUserId)) {
                throw new ForbiddenException(
                        String.format("ACCESS_DENIED: Bạn (Bệnh nhân ID %d) không có quyền truy cập hồ sơ bệnh án số %d của bệnh nhân khác! (Phát hiện vi phạm truy cập chéo IDOR)",
                                currentUserId, id));
            }
        } else if ("DOCTOR".equalsIgnoreCase(currentRole)) {
            // Bác sĩ chỉ xem được bệnh nhân do mình phụ trách (mẫu: Bác sĩ 1 khám BN 1, Bác sĩ 2 khám BN 2)
            Long doctorId = resolveDoctorId(authHeader);
            if (doctorId != null && !doctorId.equals(record.patientId())) {
                throw new ForbiddenException(
                        String.format("ACCESS_DENIED: Bác sĩ (ID %d) không phụ trách điều trị bệnh nhân của hồ sơ số %d!",
                                doctorId, id));
            }
        }

        return record;
    }

    private Long resolvePatientId(String authHeader, Long paramId) {
        if (paramId != null) return paramId;
        if (authHeader != null) {
            String lower = authHeader.toLowerCase();
            if (lower.contains("patient.an") || lower.contains("patient_an") || lower.contains("token_a")) {
                return 1L; // Bệnh nhân An
            }
            if (lower.contains("patient.binh") || lower.contains("patient_binh") || lower.contains("token_b")) {
                return 2L; // Bệnh nhân Bình
            }
            if (lower.contains("patient.chi") || lower.contains("patient_chi")) {
                return 3L; // Bệnh nhân Chi
            }
        }
        return 1L; // Mặc định nếu không chỉ định rõ
    }

    private String resolveRole(String authHeader, String paramRole) {
        if (paramRole != null && !paramRole.isBlank()) return paramRole.toUpperCase();
        if (authHeader != null) {
            String lower = authHeader.toLowerCase();
            if (lower.contains("admin")) return "ADMIN";
            if (lower.contains("doctor")) return "DOCTOR";
            if (lower.contains("patient")) return "PATIENT";
        }
        return "PATIENT";
    }

    private Long resolveDoctorId(String authHeader) {
        if (authHeader != null) {
            String lower = authHeader.toLowerCase();
            if (lower.contains("doctor.lan") || lower.contains("bs0001")) return 1L;
            if (lower.contains("doctor.huy") || lower.contains("bs0002")) return 2L;
        }
        return null;
    }
}
