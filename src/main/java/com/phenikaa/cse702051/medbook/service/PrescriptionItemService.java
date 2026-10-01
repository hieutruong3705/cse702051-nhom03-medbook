package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnauthorizedException;
import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PrescriptionItemService {

    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PrescriptionService prescriptionService;

    public PrescriptionItemService(
            PrescriptionItemRepository prescriptionItemRepository,
            PrescriptionService prescriptionService
    ) {
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.prescriptionService = prescriptionService;
    }

    /**
     * Tạo prescription item.
     * Chỉ doctor phụ trách encounter của prescription mới được thêm thuốc.
     */
    @Transactional
    public PrescriptionItem create(
            Long prescriptionId,
            PrescriptionItem item
    ) {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new IllegalArgumentException(
                    "Prescription ID không hợp lệ"
            );
        }

        if (item == null) {
            throw new IllegalArgumentException(
                    "Prescription item không được để trống"
            );
        }

        /*
         * Thêm thuốc là thao tác GHI: chỉ bác sĩ phụ trách lần khám, và lần khám phải còn OPEN
         * (bệnh nhân/bác sĩ khác → 403, đã hoàn thành → 409).
         */
        Prescription prescription =
                prescriptionService.getForEdit(prescriptionId);

        /*
         * Dòng thuốc luôn là bản ghi MỚI: không cho client truyền id (ghi đè dòng của đơn khác)
         * hay prescriptionId khác.
         */
        item.setId(null);
        item.setPrescriptionId(prescription.getId());

        if (item.getMedicineName() == null
                || item.getMedicineName().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Tên thuốc không được để trống"
            );
        }

        if (item.getQuantity() == null
                || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Số lượng thuốc phải lớn hơn 0"
            );
        }

        if (item.getDurationDays() != null
                && item.getDurationDays() <= 0) {

            throw new IllegalArgumentException(
                    "Số ngày sử dụng phải lớn hơn 0"
            );
        }

        item.setMedicineName(
                item.getMedicineName().trim()
        );

        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }

        return prescriptionItemRepository.save(item);
    }

    /**
     * Lấy prescription item theo ID.
     *
     * PrescriptionService.getById() sẽ kiểm tra:
     * - Patient: chỉ xem dữ liệu của mình.
     * - Doctor: chỉ xem encounter mình phụ trách.
     */
    @Transactional(readOnly = true)
    public PrescriptionItem getById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Prescription item ID không hợp lệ"
            );
        }

        PrescriptionItem item =
                prescriptionItemRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy prescription item với ID: "
                                                + id
                                )
                        );

        prescriptionService.getById(
                item.getPrescriptionId()
        );

        return item;
    }

    /**
     * Dòng thuốc mà người gọi được phép thay đổi: bác sĩ phụ trách và lần khám còn OPEN.
     */
    private PrescriptionItem getForEdit(Long id) {

        PrescriptionItem item =
                prescriptionItemRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Không tìm thấy prescription item với ID: "
                                                + id
                                )
                        );

        prescriptionService.getForEdit(
                item.getPrescriptionId()
        );

        return item;
    }

    /**
     * Lấy danh sách item của prescription.
     *
     * Patient chỉ được xem prescription thuộc encounter của mình.
     * Doctor chỉ được xem prescription thuộc encounter mình phụ trách.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionItem> getByPrescriptionId(
            Long prescriptionId
    ) {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new IllegalArgumentException(
                    "Prescription ID không hợp lệ"
            );
        }

        prescriptionService.getById(prescriptionId);

        return prescriptionItemRepository.findByPrescriptionId(
                prescriptionId
        );
    }

    /**
     * Tìm kiếm thuốc theo tên.
     *
     * Đây là chức năng tìm kiếm dữ liệu prescription item,
     * không dùng để truy cập trực tiếp dữ liệu ngoài quyền.
     *
     * Cố ý KHÔNG bọc giao dịch: {@link #canRead(Long)} bắt ngoại lệ từ việc kiểm quyền, và nếu
     * ngoại lệ đó đi qua ranh giới giao dịch chung thì giao dịch bị đánh dấu rollback-only (→ 500).
     * Mỗi lần kiểm quyền chạy giao dịch riêng nên ngoại lệ chỉ ảnh hưởng chính nó.
     */
    public List<PrescriptionItem> searchByMedicineName(
            String medicineName
    ) {
        if (medicineName == null
                || medicineName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Tên thuốc tìm kiếm không được để trống"
            );
        }

        assertAuthenticated();

        // Chỉ trả những dòng thuộc đơn thuốc mà người gọi được phép đọc (không lộ dữ liệu giữa các bệnh nhân).
        return prescriptionItemRepository
                .findByMedicineNameContainingIgnoreCase(
                        medicineName.trim()
                )
                .stream()
                .filter(found -> canRead(found.getPrescriptionId()))
                .toList();
    }

    private boolean canRead(Long prescriptionId) {

        try {
            prescriptionService.getById(prescriptionId);
            return true;
        } catch (ForbiddenException | ResourceNotFoundException e) {
            return false;
        }
    }

    /**
     * Cập nhật prescription item.
     * Chỉ doctor mới được sửa item.
     */
    @Transactional
    public PrescriptionItem update(
            Long id,
            PrescriptionItem request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu prescription item không được để trống"
            );
        }

        // Sửa là thao tác GHI: bác sĩ phụ trách và lần khám còn OPEN (nếu không → 403/409)
        PrescriptionItem item = getForEdit(id);

        if (request.getMedicineName() != null) {

            if (request.getMedicineName().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Tên thuốc không được để trống"
                );
            }

            item.setMedicineName(
                    request.getMedicineName().trim()
            );
        }

        if (request.getDosage() != null) {
            item.setDosage(request.getDosage());
        }

        if (request.getFrequency() != null) {
            item.setFrequency(request.getFrequency());
        }

        if (request.getDurationDays() != null) {

            if (request.getDurationDays() <= 0) {
                throw new IllegalArgumentException(
                        "Số ngày sử dụng phải lớn hơn 0"
                );
            }

            item.setDurationDays(
                    request.getDurationDays()
            );
        }

        if (request.getQuantity() != null) {

            if (request.getQuantity()
                    .compareTo(BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException(
                        "Số lượng thuốc phải lớn hơn 0"
                );
            }

            item.setQuantity(
                    request.getQuantity()
            );
        }

        if (request.getInstructions() != null) {
            item.setInstructions(
                    request.getInstructions()
            );
        }

        return prescriptionItemRepository.save(item);
    }

    /**
     * Xóa prescription item.
     * Chỉ doctor mới được xóa.
     */
    @Transactional
    public void delete(Long id) {

        PrescriptionItem item = getForEdit(id);

        prescriptionItemRepository.delete(item);
    }

    /**
     * Kiểm tra đã đăng nhập.
     */
    private void assertAuthenticated() {

        var authentication =
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new UnauthorizedException(
                    "Người dùng chưa đăng nhập"
            );
        }
    }
}
