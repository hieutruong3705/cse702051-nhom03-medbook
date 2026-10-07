package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.phenikaa.cse702051.medbook.model.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    List<Invoice> findByPatientId(Long patientId);

    boolean existsByInvoiceCode(String invoiceCode);

    boolean existsByAppointmentId(Long appointmentId);

    Optional<Invoice> findByAppointmentId(Long appointmentId);

    /**
     * Ghi nhận đã thu bằng cập nhật có điều kiện: chỉ hóa đơn đang {@code UNPAID} mới đổi được, nên hai yêu cầu
     * thu (hoặc một thu, một hủy) gửi cùng lúc chỉ có đúng một yêu cầu cập nhật được dòng.
     *
     * @return 1 nếu đã chuyển sang PAID; 0 nếu hóa đơn không tồn tại hoặc không còn UNPAID
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Invoice i set i.status = 'PAID', i.paidAt = :now, i.updatedAt = :now
            where i.id = :id and i.status = 'UNPAID'
            """)
    int markPaid(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Hủy hóa đơn bằng cập nhật có điều kiện; cùng cơ chế với {@link #markPaid}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Invoice i set i.status = 'VOID', i.voidedAt = :now, i.voidReason = :reason, i.updatedAt = :now
            where i.id = :id and i.status = 'UNPAID'
            """)
    int markVoid(@Param("id") Long id, @Param("reason") String reason, @Param("now") LocalDateTime now);

    // ---- Báo cáo doanh thu: tổng hợp ở CSDL trên hóa đơn LẬP trong [from, toExclusive) ----

    /** Mỗi dòng: {@code [String trạng thái, Long số hóa đơn, BigDecimal tổng tiền]}. */
    @Query("""
            select i.status, count(i), coalesce(sum(i.totalAmount), 0)
            from Invoice i
            where i.issuedAt >= :from and i.issuedAt < :toExclusive
            group by i.status
            """)
    List<Object[]> sumByStatus(@Param("from") LocalDateTime from, @Param("toExclusive") LocalDateTime toExclusive);

    /** Mỗi dòng: {@code [Integer năm, Integer tháng, Integer ngày, String trạng thái, Long số hóa đơn, BigDecimal tổng tiền]}. */
    @Query("""
            select year(i.issuedAt), month(i.issuedAt), day(i.issuedAt), i.status, count(i),
                   coalesce(sum(i.totalAmount), 0)
            from Invoice i
            where i.issuedAt >= :from and i.issuedAt < :toExclusive
            group by year(i.issuedAt), month(i.issuedAt), day(i.issuedAt), i.status
            order by year(i.issuedAt), month(i.issuedAt), day(i.issuedAt)
            """)
    List<Object[]> sumByDayAndStatus(@Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);

    /** Mỗi dòng: {@code [Integer năm, Integer tháng, String trạng thái, Long số hóa đơn, BigDecimal tổng tiền]}. */
    @Query("""
            select year(i.issuedAt), month(i.issuedAt), i.status, count(i), coalesce(sum(i.totalAmount), 0)
            from Invoice i
            where i.issuedAt >= :from and i.issuedAt < :toExclusive
            group by year(i.issuedAt), month(i.issuedAt), i.status
            order by year(i.issuedAt), month(i.issuedAt)
            """)
    List<Object[]> sumByMonthAndStatus(@Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive);
}
