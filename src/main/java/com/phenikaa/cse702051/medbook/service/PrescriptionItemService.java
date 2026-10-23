package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionItemDTO;
import com.phenikaa.cse702051.medbook.dto.prescription.PrescriptionItemRequest;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Medicine;
import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;

/**
 * Dòng thuốc của đơn thuốc (YCCN-18). Quyền và khóa theo trạng thái lần khám dùng chung với
 * {@link PrescriptionService}: ghi chỉ dành cho bác sĩ phụ trách khi lần khám còn OPEN.
 *
 * <p>Thuốc lấy từ danh mục khi có {@code medicineId}: thuốc phải đang hoạt động và tên được chép vào dòng thuốc
 * tại thời điểm kê, nên đổi tên hay ngừng dùng thuốc sau này không làm đổi đơn cũ. Không có {@code medicineId}
 * thì là thuốc ngoài danh mục và bắt buộc có tên. Một thuốc trong danh mục không được kê lặp trong cùng một đơn;
 * mỗi đơn tối đa {@value #MAX_ITEMS} dòng.
 */
@Service
public class PrescriptionItemService {

    static final int MAX_ITEMS = 30;
    private static final int MAX_NAME = 200;
    private static final int MAX_DOSAGE = 100;
    private static final int MAX_FREQUENCY = 100;
    private static final int MAX_INSTRUCTIONS = 500;
    private static final int MAX_QUANTITY = 999;
    private static final int MAX_DURATION_DAYS = 365;

    private final PrescriptionItemRepository itemRepository;
    private final PrescriptionService prescriptionService;
    private final MedicineService medicineService;

    public PrescriptionItemService(
            PrescriptionItemRepository itemRepository,
            PrescriptionService prescriptionService,
            MedicineService medicineService) {
        this.itemRepository = itemRepository;
        this.prescriptionService = prescriptionService;
        this.medicineService = medicineService;
    }

    // ================= Ghi =================

    @Transactional
    public PrescriptionItemDTO create(Long prescriptionId, PrescriptionItemRequest request) {
        Prescription prescription = prescriptionService.getForEdit(prescriptionId);

        PrescriptionItem item = new PrescriptionItem();
        item.setPrescriptionId(prescription.getId());
        applyMedicine(item, request.medicineId(), request.medicineName(), true);
        item.setQuantity(BigDecimal.valueOf(quantityOf(request.quantity())));
        item.setDurationDays(durationOf(request.durationDays()));
        item.setDosage(textOf("dosage", "Liều dùng", request.dosage(), MAX_DOSAGE));
        item.setFrequency(textOf("frequency", "Tần suất", request.frequency(), MAX_FREQUENCY));
        item.setInstructions(textOf("instructions", "Hướng dẫn sử dụng", request.instructions(), MAX_INSTRUCTIONS));

        if (item.getMedicineId() != null
                && itemRepository.existsByPrescriptionIdAndMedicineId(prescriptionId, item.getMedicineId())) {
            throw new FieldValidationException("medicineId", "Thuốc này đã có trong đơn");
        }
        if (itemRepository.countByPrescriptionId(prescriptionId) >= MAX_ITEMS) {
            throw new FieldValidationException("items", "Mỗi đơn thuốc tối đa " + MAX_ITEMS + " dòng thuốc");
        }
        item.setCreatedAt(LocalDateTime.now());
        item = itemRepository.saveAndFlush(item);
        prescriptionService.auditWrite(prescription, "ITEM_ADDED", item.getId());
        return PrescriptionItemDTO.from(item);
    }

    @Transactional
    public PrescriptionItemDTO update(Long id, PrescriptionItemRequest request) {
        PrescriptionItem item = find(id);
        Prescription prescription = prescriptionService.getForEdit(item.getPrescriptionId());

        if (request.medicineId() != null || request.medicineName() != null) {
            applyMedicine(item, request.medicineId(), request.medicineName(), false);
            if (item.getMedicineId() != null && itemRepository.existsByPrescriptionIdAndMedicineIdAndIdNot(
                    item.getPrescriptionId(), item.getMedicineId(), id)) {
                throw new FieldValidationException("medicineId", "Thuốc này đã có trong đơn");
            }
        }
        if (request.quantity() != null) {
            item.setQuantity(BigDecimal.valueOf(quantityOf(request.quantity())));
        }
        if (request.durationDays() != null) {
            item.setDurationDays(durationOf(request.durationDays()));
        }
        if (request.dosage() != null) {
            item.setDosage(textOf("dosage", "Liều dùng", request.dosage(), MAX_DOSAGE));
        }
        if (request.frequency() != null) {
            item.setFrequency(textOf("frequency", "Tần suất", request.frequency(), MAX_FREQUENCY));
        }
        if (request.instructions() != null) {
            item.setInstructions(textOf("instructions", "Hướng dẫn sử dụng", request.instructions(),
                    MAX_INSTRUCTIONS));
        }
        item = itemRepository.saveAndFlush(item);
        prescriptionService.auditWrite(prescription, "ITEM_UPDATED", item.getId());
        return PrescriptionItemDTO.from(item);
    }

    @Transactional
    public void delete(Long id) {
        PrescriptionItem item = find(id);
        Prescription prescription = prescriptionService.getForEdit(item.getPrescriptionId());
        itemRepository.delete(item);
        prescriptionService.auditWrite(prescription, "ITEM_REMOVED", id);
    }

    // ================= Đọc =================

    @Transactional(readOnly = true)
    public List<PrescriptionItemDTO> listByPrescription(Long prescriptionId) {
        Prescription prescription = prescriptionService.getReadable(prescriptionId);
        prescriptionService.auditView(prescription.getEncounterId(), 1);
        return itemRepository.findByPrescriptionIdOrderByIdAsc(prescriptionId).stream()
                .map(PrescriptionItemDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionItemDTO get(Long id) {
        PrescriptionItem item = find(id);
        Prescription prescription = prescriptionService.getReadable(item.getPrescriptionId());
        prescriptionService.auditView(prescription.getEncounterId(), 1);
        return PrescriptionItemDTO.from(item);
    }

    // ================= Chi tiết =================

    private PrescriptionItem find(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng thuốc!"));
    }

    /**
     * Gán thuốc cho dòng: từ danh mục (chép tên tại thời điểm kê) hoặc thuốc ngoài danh mục theo tên nhập tay.
     *
     * @param creating khi tạo mới, thiếu cả {@code medicineId} lẫn tên thuốc là lỗi
     */
    private void applyMedicine(PrescriptionItem item, Long medicineId, String medicineName, boolean creating) {
        if (medicineId != null) {
            Medicine medicine = medicineService.requireActive(medicineId);
            item.setMedicineId(medicine.getId());
            item.setMedicineName(medicine.getName());
            return;
        }
        if (medicineName == null || medicineName.isBlank()) {
            if (creating || medicineName != null) {
                throw new FieldValidationException("medicineName", "Tên thuốc không được để trống");
            }
            return;
        }
        String name = medicineName.trim();
        if (name.length() > MAX_NAME) {
            throw new FieldValidationException("medicineName", "Tên thuốc tối đa " + MAX_NAME + " ký tự");
        }
        item.setMedicineId(null);
        item.setMedicineName(name);
    }

    private static int quantityOf(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new FieldValidationException("quantity", "Số lượng thuốc phải lớn hơn 0");
        }
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new FieldValidationException("quantity", "Số lượng thuốc phải là số nguyên");
        }
        if (quantity.compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0) {
            throw new FieldValidationException("quantity", "Số lượng thuốc tối đa " + MAX_QUANTITY);
        }
        return quantity.intValueExact();
    }

    private static Integer durationOf(BigDecimal durationDays) {
        if (durationDays == null) {
            return null;
        }
        if (durationDays.signum() <= 0) {
            throw new FieldValidationException("durationDays", "Số ngày sử dụng phải lớn hơn 0");
        }
        if (durationDays.stripTrailingZeros().scale() > 0) {
            throw new FieldValidationException("durationDays", "Số ngày sử dụng phải là số nguyên");
        }
        if (durationDays.compareTo(BigDecimal.valueOf(MAX_DURATION_DAYS)) > 0) {
            throw new FieldValidationException("durationDays", "Số ngày sử dụng tối đa " + MAX_DURATION_DAYS + " ngày");
        }
        return durationDays.intValueExact();
    }

    private static String textOf(String field, String label, String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new FieldValidationException(field, label + " tối đa " + max + " ký tự");
        }
        return trimmed;
    }
}
