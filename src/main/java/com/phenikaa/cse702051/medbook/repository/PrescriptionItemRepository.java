package com.phenikaa.cse702051.medbook.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.phenikaa.cse702051.medbook.model.PrescriptionItem;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    List<PrescriptionItem> findByPrescriptionId(Long prescriptionId);

    /** Các dòng thuốc của một đơn theo thứ tự kê. */
    List<PrescriptionItem> findByPrescriptionIdOrderByIdAsc(Long prescriptionId);

    /** Dòng thuốc của nhiều đơn trong một truy vấn (dựng danh sách đơn của một lần khám mà không N+1). */
    List<PrescriptionItem> findByPrescriptionIdInOrderByIdAsc(Collection<Long> prescriptionIds);

    long countByPrescriptionId(Long prescriptionId);

    boolean existsByPrescriptionId(Long prescriptionId);

    /** Cùng một thuốc trong danh mục đã có trong đơn hay chưa (không kê lặp một thuốc trong một đơn). */
    boolean existsByPrescriptionIdAndMedicineId(Long prescriptionId, Long medicineId);

    boolean existsByPrescriptionIdAndMedicineIdAndIdNot(Long prescriptionId, Long medicineId, Long id);

    /** Thuốc trong danh mục đã được kê trong dòng đơn nào hay chưa (quyết định xóa hẳn hay chỉ ngừng sử dụng). */
    boolean existsByMedicineId(Long medicineId);
}
