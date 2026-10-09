package com.phenikaa.cse702051.medbook;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.repository.JpaPatientRepository;
import com.phenikaa.cse702051.medbook.service.InvoiceService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private JpaPatientRepository patientRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private Patient patient;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setId(1L);

        appointment = new Appointment();
        appointment.setId(10L);
        appointment.setPatientId(1L);
    }

    @Test
    void createInvoice_validData_savesCorrectTotal() {
        when(patientRepository.findById(1L))
                .thenReturn(Optional.of(patient));

        when(appointmentRepository.findById(10L))
                .thenReturn(Optional.of(appointment));

        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.createInvoice(
                1L,
                10L,
                new BigDecimal("500.00"),
                new BigDecimal("50.00"));

        assertNotNull(result);
        assertNotNull(result.getInvoiceCode());
        assertEquals(new BigDecimal("500.00"), result.getSubtotal());
        assertEquals(new BigDecimal("50.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("450.00"), result.getTotalAmount());
        assertEquals(patient, result.getPatient());
        assertEquals(appointment, result.getAppointment());
        assertEquals("UNPAID", result.getStatus());
        assertNotNull(result.getIssuedAt());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());

        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void createInvoice_negativeSubtotal_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        10L,
                        new BigDecimal("-1.00"),
                        BigDecimal.ZERO));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_negativeDiscount_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        10L,
                        new BigDecimal("100.00"),
                        new BigDecimal("-1.00")));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_discountGreaterThanSubtotal_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        10L,
                        new BigDecimal("100.00"),
                        new BigDecimal("101.00")));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_patientNotFound_returns404() {
        when(patientRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        99L,
                        10L,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO));

        assertEquals(404, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_appointmentNotFound_returns404() {
        when(patientRepository.findById(1L))
                .thenReturn(Optional.of(patient));

        when(appointmentRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        99L,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO));

        assertEquals(404, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_patientDoesNotMatchAppointment_returns400() {
        appointment.setPatientId(2L);

        when(patientRepository.findById(1L))
                .thenReturn(Optional.of(patient));

        when(appointmentRepository.findById(10L))
                .thenReturn(Optional.of(appointment));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        10L,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_nullDiscount_defaultsToZero() {
        when(patientRepository.findById(1L))
                .thenReturn(Optional.of(patient));

        when(appointmentRepository.findById(10L))
                .thenReturn(Optional.of(appointment));

        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Invoice result = invoiceService.createInvoice(
                1L,
                10L,
                new BigDecimal("100.00"),
                null);

        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertEquals(new BigDecimal("100.00"), result.getTotalAmount());

        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    void createInvoice_nullPatientId_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        null,
                        10L,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_nullAppointmentId_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        null,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    void createInvoice_nullSubtotal_returns400() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> invoiceService.createInvoice(
                        1L,
                        10L,
                        null,
                        BigDecimal.ZERO));

        assertEquals(400, ex.getStatusCode().value());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }
}