package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceId(Long invoiceId);

    List<InvoiceItem> findByServiceId(Long serviceId);

    List<InvoiceItem> findByInvoiceIdAndServiceId(
            Long invoiceId,
            Long serviceId
    );

    boolean existsByInvoiceId(Long invoiceId);
}