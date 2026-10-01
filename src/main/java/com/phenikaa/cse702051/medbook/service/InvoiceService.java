package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.repository.JpaPatientRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final AppointmentRepository appointmentRepository;
   private final JpaPatientRepository patientRepository;

    public Invoice createInvoice(
            Long patientId,
            Long appointmentId,
            BigDecimal subtotal,
            BigDecimal discountAmount) {

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy bệnh nhân"));

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy lịch khám"));

        BigDecimal discount = discountAmount == null
                ? BigDecimal.ZERO
                : discountAmount;

        BigDecimal total = subtotal.subtract(discount);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tổng tiền không được âm");
        }

        LocalDateTime now = LocalDateTime.now();

        Invoice invoice = new Invoice();
        invoice.setInvoiceCode(
                "INV-" + UUID.randomUUID().toString()
                        .substring(0, 8)
                        .toUpperCase());
        invoice.setPatient(patient);
        invoice.setAppointment(appointment);
        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discount);
        invoice.setTotalAmount(total);
        invoice.setStatus("UNPAID");
        invoice.setIssuedAt(now);
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);

        return invoiceRepository.save(invoice);
    }
}