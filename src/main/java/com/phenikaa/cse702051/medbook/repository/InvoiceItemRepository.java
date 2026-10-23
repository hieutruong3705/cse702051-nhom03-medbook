package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.model.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceId(Long invoiceId);

    /** Các dòng của một hóa đơn theo thứ tự lập. */
    List<InvoiceItem> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    boolean existsByInvoiceId(Long invoiceId);

    /** Dịch vụ đã xuất hiện trong dòng hóa đơn nào hay chưa (quyết định xóa hẳn hay chỉ ngừng sử dụng). */
    boolean existsByServiceId(Long serviceId);

    /**
     * Tổng hợp theo dịch vụ trên các hóa đơn lập trong {@code [start, end)} và không ở trạng thái
     * {@code excludedStatus}: {@code [serviceId, mã, tên, số hóa đơn, tổng số lượng, tổng thành tiền]}, giá trị
     * lớn nhất trước.
     */
    @Query("""
            select it.serviceId, s.code, s.name, count(distinct i.id), sum(it.quantity), sum(it.lineTotal)
            from InvoiceItem it
            join Invoice i on i.id = it.invoiceId
            left join MedicalService s on s.id = it.serviceId
            where i.issuedAt >= :start and i.issuedAt < :end and i.status <> :excludedStatus
            group by it.serviceId, s.code, s.name
            order by sum(it.lineTotal) desc, it.serviceId asc
            """)
    List<Object[]> sumByService(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("excludedStatus") String excludedStatus);
}
