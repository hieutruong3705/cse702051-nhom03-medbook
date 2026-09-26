package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import com.phenikaa.cse702051.medbook.model.MedicalRecord;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceItemRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterService encounterService;
    private final MedicalRecordService medicalRecordService;
    private final Dev4AuthorizationService authorizationService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            EncounterRepository encounterRepository,
            EncounterService encounterService,
            MedicalRecordService medicalRecordService,
            Dev4AuthorizationService authorizationService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.encounterRepository = encounterRepository;
        this.encounterService = encounterService;
        this.medicalRecordService = medicalRecordService;
        this.authorizationService = authorizationService;
    }

    // ============================================================
    // CREATE INVOICE
    // ============================================================

    @Transactional
    public Invoice createInvoice(
            Long encounterId,
            List<InvoiceItem> items,
            BigDecimal discountAmount
    ) {

        if (encounterId == null || encounterId <= 0) {
            throw new IllegalArgumentException(
                    "Encounter ID không hợp lệ"
            );
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "Hóa đơn phải có ít nhất một sản phẩm/dịch vụ"
            );
        }

        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }

        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Discount không được âm"
            );
        }

        // ========================================================
        // 1. KIỂM TRA ENCOUNTER
        // ========================================================

        Encounter encounter =
                encounterService.getById(encounterId);

        // Chỉ doctor phụ trách encounter mới được tạo invoice
        authorizationService.assertDoctorOwnsEncounter(
                encounter
        );

        // ========================================================
        // 2. LẤY MEDICAL RECORD
        // ========================================================

        MedicalRecord medicalRecord =
                medicalRecordService.findById(
                        encounter.getMedicalRecordId()
                );

        Long patientId =
                medicalRecord.getPatientId();

        if (patientId == null) {
            throw new IllegalArgumentException(
                    "Medical record chưa có patient ID"
            );
        }

        // ========================================================
        // 3. MỘT APPOINTMENT CHỈ GẮN MỘT INVOICE
        // ========================================================

        Long appointmentId =
                encounter.getAppointmentId();

        if (appointmentId != null
                && invoiceRepository.existsByAppointmentId(
                        appointmentId
                )) {

            throw new IllegalArgumentException(
                    "Appointment này đã có hóa đơn"
            );
        }

        // ========================================================
        // 4. TÍNH SUBTOTAL
        // ========================================================

        BigDecimal subtotal =
                BigDecimal.ZERO;

        for (InvoiceItem item : items) {

            if (item == null) {
                throw new IllegalArgumentException(
                        "Invoice item không được null"
                );
            }

            if (item.getDescription() == null
                    || item.getDescription()
                    .trim()
                    .isEmpty()) {

                throw new IllegalArgumentException(
                        "Description của invoice item không được để trống"
                );
            }

            if (item.getQuantity() == null
                    || item.getQuantity()
                    .compareTo(BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException(
                        "Quantity phải lớn hơn 0"
                );
            }

            if (item.getUnitPrice() == null
                    || item.getUnitPrice()
                    .compareTo(BigDecimal.ZERO) < 0) {

                throw new IllegalArgumentException(
                        "Unit price không được âm"
                );
            }

            BigDecimal lineTotal =
                    item.getQuantity()
                            .multiply(
                                    item.getUnitPrice()
                            );

            item.setDescription(
                    item.getDescription().trim()
            );

            item.setLineTotal(lineTotal);

            subtotal =
                    subtotal.add(lineTotal);
        }

        // ========================================================
        // 5. TÍNH TOTAL
        // ========================================================

        BigDecimal totalAmount =
                subtotal.subtract(
                        discountAmount
                );

        if (totalAmount.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            throw new IllegalArgumentException(
                    "Discount không được lớn hơn subtotal"
            );
        }

        // ========================================================
        // 6. TẠO INVOICE
        // ========================================================

        Invoice invoice =
                new Invoice();

        invoice.setInvoiceCode(
                generateInvoiceCode()
        );

        invoice.setPatientId(
                patientId
        );

        invoice.setAppointmentId(
                appointmentId
        );

        invoice.setSubtotal(
                subtotal
        );

        invoice.setDiscountAmount(
                discountAmount
        );

        invoice.setTotalAmount(
                totalAmount
        );

        invoice.setStatus(
                "UNPAID"
        );

        invoice.setIssuedAt(
                LocalDateTime.now()
        );

        Invoice savedInvoice =
                invoiceRepository.save(
                        invoice
                );

        // ========================================================
        // 7. LƯU INVOICE ITEMS
        // ========================================================

        LocalDateTime now =
                LocalDateTime.now();

        for (InvoiceItem item : items) {

            item.setInvoiceId(
                    savedInvoice.getId()
            );

            if (item.getCreatedAt() == null) {
                item.setCreatedAt(now);
            }

            invoiceItemRepository.save(item);
        }

        return savedInvoice;
    }

    // ============================================================
    // GET INVOICE BY ID
    // ============================================================

    @Transactional(readOnly = true)
    public Invoice getInvoiceById(
            Long invoiceId
    ) {

        if (invoiceId == null || invoiceId <= 0) {
            throw new IllegalArgumentException(
                    "Invoice ID không hợp lệ"
            );
        }

        Invoice invoice =
                invoiceRepository.findById(
                        invoiceId
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy hóa đơn với ID: "
                                        + invoiceId
                        )
                );

        assertInvoiceAccess(invoice);

        return invoice;
    }

    // ============================================================
    // GET INVOICE ITEMS
    // ============================================================

    @Transactional(readOnly = true)
    public List<InvoiceItem> getInvoiceItems(
            Long invoiceId
    ) {

        getInvoiceById(invoiceId);

        return invoiceItemRepository.findByInvoiceId(
                invoiceId
        );
    }

    // ============================================================
    // GET MY INVOICES
    // ============================================================

    @Transactional(readOnly = true)
    public List<Invoice> getMyInvoices() {

        if (!isPatient()) {
            throw new IllegalArgumentException(
                    "Chỉ bệnh nhân mới được sử dụng chức năng này"
            );
        }

        Long currentPatientId =
                authorizationService.getCurrentPatientId();

        return invoiceRepository.findByPatientId(
                currentPatientId
        );
    }

    // ============================================================
    // GET INVOICES BY PATIENT
    // ============================================================

    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesByPatient(
            Long patientId
    ) {

        if (!isPatient()) {
            throw new IllegalArgumentException(
                    "Chỉ bệnh nhân mới được sử dụng chức năng này"
            );
        }

        Long currentPatientId =
                authorizationService.getCurrentPatientId();

        if (!currentPatientId.equals(patientId)) {
            throw new IllegalArgumentException(
                    "Bạn không có quyền xem hóa đơn của bệnh nhân khác"
            );
        }

        return invoiceRepository.findByPatientId(
                currentPatientId
        );
    }

    // ============================================================
    // GET ALL INVOICES - ADMIN
    // ============================================================

    @Transactional(readOnly = true)
    public List<Invoice> getAllInvoices() {

        return invoiceRepository.findAll();
    }

    // ============================================================
    // MARK AS PAID
    // ============================================================

    @Transactional
    public Invoice markAsPaid(
            Long invoiceId
    ) {

        Invoice invoice =
                getInvoiceById(invoiceId);

        if ("VOID".equalsIgnoreCase(
                invoice.getStatus()
        )) {

            throw new IllegalArgumentException(
                    "Không thể thanh toán hóa đơn đã VOID"
            );
        }

        if ("PAID".equalsIgnoreCase(
                invoice.getStatus()
        )) {

            return invoice;
        }

        invoice.setStatus("PAID");

        invoice.setPaidAt(
                LocalDateTime.now()
        );

        return invoiceRepository.save(
                invoice
        );
    }

    // ============================================================
    // VOID INVOICE
    // ============================================================

    @Transactional
    public Invoice voidInvoice(
            Long invoiceId
    ) {

        Invoice invoice =
                getInvoiceById(invoiceId);

        if ("PAID".equalsIgnoreCase(
                invoice.getStatus()
        )) {

            throw new IllegalArgumentException(
                    "Không thể VOID hóa đơn đã thanh toán"
            );
        }

        invoice.setStatus("VOID");

        return invoiceRepository.save(
                invoice
        );
    }

    // ============================================================
    // INVOICE AUTHORIZATION
    // ============================================================

    private void assertInvoiceAccess(
            Invoice invoice
    ) {

        if (invoice == null) {
            throw new IllegalArgumentException(
                    "Invoice không tồn tại"
            );
        }

        // --------------------------------------------------------
        // PATIENT
        // --------------------------------------------------------

        if (isPatient()) {

            Long currentPatientId =
                    authorizationService.getCurrentPatientId();

            if (!currentPatientId.equals(
                    invoice.getPatientId()
            )) {

                throw new IllegalArgumentException(
                        "Bạn không có quyền truy cập hóa đơn này"
                );
            }

            return;
        }

        // --------------------------------------------------------
        // DOCTOR
        // --------------------------------------------------------

        if (isDoctor()) {

            Long appointmentId =
                    invoice.getAppointmentId();

            if (appointmentId == null) {
                throw new IllegalArgumentException(
                        "Hóa đơn này không gắn với appointment"
                );
            }

            Encounter encounter =
                    encounterRepository
                            .findByAppointmentId(
                                    appointmentId
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Không tìm thấy encounter của hóa đơn"
                                    )
                            );

            authorizationService.assertDoctorOwnsEncounter(
                    encounter
            );

            return;
        }

        throw new IllegalArgumentException(
                "Bạn không có quyền truy cập hóa đơn này"
        );
    }

    // ============================================================
    // ROLE HELPERS
    // ============================================================

    private boolean isPatient() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_PATIENT".equals(
                                authority.getAuthority()
                        )
                );
    }

    private boolean isDoctor() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_DOCTOR".equals(
                                authority.getAuthority()
                        )
                );
    }

    // ============================================================
    // GENERATE INVOICE CODE
    // ============================================================

    private String generateInvoiceCode() {

        String code;

        do {
            code =
                    "INV-"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 8)
                            .toUpperCase();

        } while (
                invoiceRepository
                        .existsByInvoiceCode(code)
        );

        return code;
    }
}