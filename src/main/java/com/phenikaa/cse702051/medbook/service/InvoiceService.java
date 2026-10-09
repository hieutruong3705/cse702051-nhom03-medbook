package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public Invoice createInvoice(
            Long patientId,
            Long appointmentId,
            BigDecimal subtotal,
            BigDecimal discountAmount) {

        if (patientId == null || appointmentId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Patient ID and appointment ID are required");
        }

        if (subtotal == null
                || subtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Subtotal must not be null or negative");
        }

        BigDecimal discount = discountAmount == null
                ? BigDecimal.ZERO
                : discountAmount;

        if (discount.compareTo(BigDecimal.ZERO) < 0
                || discount.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Discount must be between zero and subtotal");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Patient not found"));

        Appointment appointment = appointmentRepository
                .findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Appointment not found"));

        if (!patientId.equals(appointment.getPatientId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Patient does not match the appointment");
        }

        BigDecimal total = subtotal.subtract(discount);

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
