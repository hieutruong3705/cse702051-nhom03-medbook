package com.phenikaa.cse702051.medbook.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.phenikaa.cse702051.medbook.dto.catalog.ServicePriceSnapshot;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.model.Medicine;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.MedicineRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.service.MedicalServiceService;
import com.phenikaa.cse702051.medbook.service.MedicineService;
import com.phenikaa.cse702051.medbook.service.SpecialtyService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-01.6: các hàm nội bộ mà module khác (hóa đơn, đơn thuốc, hồ sơ bác sĩ) gọi không bao giờ trả {@code null};
 * bản ghi không tồn tại hoặc đã ngừng sử dụng ném {@link FieldValidationException} kèm đúng tên trường.
 */
class CatalogInternalApiTest extends AbstractApiTest {

    @Autowired
    private MedicalServiceService medicalServiceService;
    @Autowired
    private MedicineService medicineService;
    @Autowired
    private SpecialtyService specialtyService;
    @Autowired
    private MedicalServiceRepository services;
    @Autowired
    private MedicineRepository medicines;
    @Autowired
    private SpecialtyRepository specialties;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    @Test
    @DisplayName("getPriceSnapshot: trả tên và đơn giá scale 2; không tồn tại hoặc INACTIVE → lỗi ở trường serviceId")
    void priceSnapshot() {
        String token = unique();
        MedicalService active = services.save(MedicalService.builder().code("IPA" + token).name("Chụp X-quang")
                .durationMinutes(15).price(new BigDecimal("180000.5")).status("ACTIVE").build());
        MedicalService inactive = services.save(MedicalService.builder().code("IPI" + token).name("Đã ngừng")
                .durationMinutes(15).price(BigDecimal.TEN).status("INACTIVE").build());

        ServicePriceSnapshot snapshot = medicalServiceService.getPriceSnapshot(active.getId());
        assertEquals(active.getId(), snapshot.serviceId());
        assertEquals("Chụp X-quang", snapshot.name());
        assertEquals(new BigDecimal("180000.50"), snapshot.price());

        for (Long id : new Long[] { inactive.getId(), 999_999L, null }) {
            FieldValidationException error = assertThrows(FieldValidationException.class,
                    () -> medicalServiceService.getPriceSnapshot(id));
            assertEquals(Map.of("serviceId", error.getMessage()), error.getDetails());
        }
    }

    @Test
    @DisplayName("requireActive: thuốc và chuyên khoa đang hoạt động được trả về; không tồn tại hoặc INACTIVE → lỗi đúng trường")
    void requireActive() {
        String token = unique();
        Medicine activeMedicine = medicines.save(Medicine.builder().code("IMA" + token).name("Cetirizine 10mg")
                .unit("Viên").status("ACTIVE").build());
        Medicine inactiveMedicine = medicines.save(Medicine.builder().code("IMI" + token).name("Đã ngừng")
                .status("INACTIVE").build());

        assertEquals("Cetirizine 10mg", medicineService.requireActive(activeMedicine.getId()).getName());
        for (Long id : new Long[] { inactiveMedicine.getId(), 999_999L, null }) {
            FieldValidationException error = assertThrows(FieldValidationException.class,
                    () -> medicineService.requireActive(id));
            assertEquals(Map.of("medicineId", error.getMessage()), error.getDetails());
        }

        Specialty activeSpecialty = specialties.save(Specialty.builder().code("ISA" + token).name("Thần kinh")
                .status("ACTIVE").build());
        Specialty inactiveSpecialty = specialties.save(Specialty.builder().code("ISI" + token).name("Đã ngừng")
                .status("INACTIVE").build());
        assertEquals("Thần kinh", specialtyService.requireActive(activeSpecialty.getId()).getName());
        for (Long id : new Long[] { inactiveSpecialty.getId(), 999_999L, null }) {
            FieldValidationException error = assertThrows(FieldValidationException.class,
                    () -> specialtyService.requireActive(id));
            assertEquals(Map.of("specialtyId", error.getMessage()), error.getDetails());
        }
    }
}
