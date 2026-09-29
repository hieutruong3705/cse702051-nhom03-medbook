package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceCode(String invoiceCode);

    List<Invoice> findByPatientId(Long patientId);

    List<Invoice> findByStatus(String status);

    List<Invoice> findByPatientIdAndStatus(
            Long patientId,
            String status
    );

    boolean existsByInvoiceCode(String invoiceCode);

    boolean existsByAppointmentId(Long appointmentId);
}