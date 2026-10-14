package com.phenikaa.cse702051.medbook.service;

import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionPatientDTO;
import com.phenikaa.cse702051.medbook.dto.reception.ReceptionPatientRequest;
import com.phenikaa.cse702051.medbook.exception.FieldConflictException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;
import com.phenikaa.cse702051.medbook.util.SearchTerms;

/**
 * Bệnh nhân tại quầy lễ tân: tra cứu mọi bệnh nhân theo họ tên, mã hoặc điện thoại và tạo hồ sơ cho người chưa có
 * tài khoản.
 *
 * <ul>
 * <li>Chỉ vai trò RECEPTIONIST; kiểm ở cả {@code SecurityConfig} lẫn đầu mỗi phương thức.</li>
 * <li>Chỉ trả trường hành chính ({@link ReceptionPatientDTO}); lễ tân không đọc thông tin y tế.</li>
 * <li>Tạo hồ sơ, tạo bệnh án rỗng và ghi nhật ký nằm trong một giao dịch.</li>
 * </ul>
 */
@Service
public class ReceptionPatientService {

    private static final String ROLE = "RECEPTIONIST";
    /** Thứ tự cố định, có khóa chính làm tiêu chí phụ để các trang không lặp hay sót dòng. */
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "fullName").and(Sort.by(Sort.Direction.ASC, "id"));

    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public ReceptionPatientService(
            PatientRepository patientRepository,
            PatientService patientService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService) {
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceptionPatientDTO> search(String keyword, int page, int size) {
        currentUserService.requireRole(ROLE);
        return PageResponse.from(
                patientRepository.searchForReception(SearchTerms.toLikePattern(keyword),
                        PageRequests.of(page, size, BY_NAME)),
                ReceptionPatientDTO::from);
    }

    @Transactional(readOnly = true)
    public ReceptionPatientDTO get(Long id) {
        currentUserService.requireRole(ROLE);
        return ReceptionPatientDTO.from(patientRepository.findForReceptionById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân ID: " + id)));
    }

    /**
     * Tạo hồ sơ bệnh nhân không gắn tài khoản. Trùng họ tên (không phân biệt hoa thường), ngày sinh và số điện
     * thoại với hồ sơ đã có → 409 tại trường {@code phone}, để quầy tìm lại hồ sơ cũ thay vì tạo bản sao.
     */
    @Transactional
    public ReceptionPatientDTO create(ReceptionPatientRequest request) {
        currentUserService.requireRole(ROLE);
        String fullName = request.fullName().trim();
        String phone = request.phone().trim();
        String email = blankToNull(request.email());
        if (patientRepository.existsByNameBirthAndPhone(fullName.toLowerCase(Locale.ROOT), request.dateOfBirth(),
                phone)) {
            throw new FieldConflictException("phone",
                    "Đã có hồ sơ bệnh nhân trùng họ tên, ngày sinh và số điện thoại này");
        }

        Patient patient = patientService.createWithoutAccount(
                fullName,
                request.dateOfBirth(),
                request.genderCode().trim().toUpperCase(Locale.ROOT),
                phone,
                email == null ? null : email.toLowerCase(Locale.ROOT),
                blankToNull(request.address()));

        // chỉ ghi ID: họ tên và điện thoại là thông tin cá nhân, không đưa vào nhật ký
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.PATIENT_CREATE,
                AuditActions.ENTITY_PATIENTS, patient.getId()));
        return ReceptionPatientDTO.from(patient);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
