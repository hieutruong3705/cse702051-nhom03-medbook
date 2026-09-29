package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.model.Prescription;
import com.phenikaa.cse702051.medbook.model.PrescriptionItem;
import com.phenikaa.cse702051.medbook.repository.PrescriptionItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PrescriptionItemService {

    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PrescriptionService prescriptionService;

    public PrescriptionItemService(
            PrescriptionItemRepository prescriptionItemRepository,
            PrescriptionService prescriptionService
    ) {
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.prescriptionService = prescriptionService;
    }

    /**
     * Táº¡o prescription item.
     * Chá»‰ doctor phá»¥ trĂ¡ch encounter cá»§a prescription má»›i Ä‘Æ°á»£c thĂªm thuá»‘c.
     */
    @Transactional
    public PrescriptionItem create(
            Long prescriptionId,
            PrescriptionItem item
    ) {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new IllegalArgumentException(
                    "Prescription ID khĂ´ng há»£p lá»‡"
            );
        }

        if (item == null) {
            throw new IllegalArgumentException(
                    "Prescription item khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng"
            );
        }

        /*
         * getById() cá»§a PrescriptionService Ä‘Ă£ kiá»ƒm tra quyá»n Ä‘á»c.
         * Tuy nhiĂªn create lĂ  thao tĂ¡c ghi nĂªn pháº£i xĂ¡c nháº­n
         * doctor sá»Ÿ há»¯u encounter thĂ´ng qua PrescriptionService.update/create
         * á»Ÿ táº§ng authorization cá»§a prescription.
         */
        Prescription prescription =
                prescriptionService.getById(prescriptionId);

        /*
         * Patient cĂ³ thá»ƒ Ä‘á»c prescription nhÆ°ng khĂ´ng Ä‘Æ°á»£c táº¡o item.
         * PrescriptionService khĂ´ng expose trá»±c tiáº¿p doctor-check,
         * vĂ¬ váº­y kiá»ƒm tra role trÆ°á»›c khi thao tĂ¡c ghi.
         */
        assertDoctorRole();

        /*
         * Láº¥y láº¡i prescription sau khi Ä‘Ă£ xĂ¡c nháº­n quyá»n.
         * KhĂ´ng cho client tá»± truyá»n prescriptionId khĂ¡c.
         */
        item.setPrescriptionId(prescription.getId());

        if (item.getMedicineName() == null
                || item.getMedicineName().trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "TĂªn thuá»‘c khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng"
            );
        }

        if (item.getQuantity() == null
                || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Sá»‘ lÆ°á»£ng thuá»‘c pháº£i lá»›n hÆ¡n 0"
            );
        }

        if (item.getDurationDays() != null
                && item.getDurationDays() <= 0) {

            throw new IllegalArgumentException(
                    "Sá»‘ ngĂ y sá»­ dá»¥ng pháº£i lá»›n hÆ¡n 0"
            );
        }

        item.setMedicineName(
                item.getMedicineName().trim()
        );

        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }

        return prescriptionItemRepository.save(item);
    }

    /**
     * Láº¥y prescription item theo ID.
     *
     * PrescriptionService.getById() sáº½ kiá»ƒm tra:
     * - Patient: chá»‰ xem dá»¯ liá»‡u cá»§a mĂ¬nh.
     * - Doctor: chá»‰ xem encounter mĂ¬nh phá»¥ trĂ¡ch.
     */
    @Transactional(readOnly = true)
    public PrescriptionItem getById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "Prescription item ID khĂ´ng há»£p lá»‡"
            );
        }

        PrescriptionItem item =
                prescriptionItemRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "KhĂ´ng tĂ¬m tháº¥y prescription item vá»›i ID: "
                                                + id
                                )
                        );

        prescriptionService.getById(
                item.getPrescriptionId()
        );

        return item;
    }

    /**
     * Láº¥y danh sĂ¡ch item cá»§a prescription.
     *
     * Patient chá»‰ Ä‘Æ°á»£c xem prescription thuá»™c encounter cá»§a mĂ¬nh.
     * Doctor chá»‰ Ä‘Æ°á»£c xem prescription thuá»™c encounter mĂ¬nh phá»¥ trĂ¡ch.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionItem> getByPrescriptionId(
            Long prescriptionId
    ) {
        if (prescriptionId == null || prescriptionId <= 0) {
            throw new IllegalArgumentException(
                    "Prescription ID khĂ´ng há»£p lá»‡"
            );
        }

        prescriptionService.getById(prescriptionId);

        return prescriptionItemRepository.findByPrescriptionId(
                prescriptionId
        );
    }

    /**
     * TĂ¬m kiáº¿m thuá»‘c theo tĂªn.
     *
     * ÄĂ¢y lĂ  chá»©c nÄƒng tĂ¬m kiáº¿m dá»¯ liá»‡u prescription item,
     * khĂ´ng dĂ¹ng Ä‘á»ƒ truy cáº­p trá»±c tiáº¿p dá»¯ liá»‡u ngoĂ i quyá»n.
     */
    @Transactional(readOnly = true)
    public List<PrescriptionItem> searchByMedicineName(
            String medicineName
    ) {
        if (medicineName == null
                || medicineName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "TĂªn thuá»‘c tĂ¬m kiáº¿m khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng"
            );
        }

        assertAuthenticated();

        return prescriptionItemRepository
                .findByMedicineNameContainingIgnoreCase(
                        medicineName.trim()
                );
    }

    /**
     * Cáº­p nháº­t prescription item.
     * Chá»‰ doctor má»›i Ä‘Æ°á»£c sá»­a item.
     */
    @Transactional
    public PrescriptionItem update(
            Long id,
            PrescriptionItem request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dá»¯ liá»‡u prescription item khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng"
            );
        }

        PrescriptionItem item = getById(id);

        assertDoctorRole();

        /*
         * Äáº£m báº£o prescription váº«n tá»“n táº¡i vĂ  quyá»n truy cáº­p
         * Ä‘á»‘i vá»›i prescription Ä‘Ă£ Ä‘Æ°á»£c kiá»ƒm tra.
         */
        prescriptionService.getById(
                item.getPrescriptionId()
        );

        if (request.getMedicineName() != null) {

            if (request.getMedicineName().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "TĂªn thuá»‘c khĂ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng"
                );
            }

            item.setMedicineName(
                    request.getMedicineName().trim()
            );
        }

        if (request.getDosage() != null) {
            item.setDosage(request.getDosage());
        }

        if (request.getFrequency() != null) {
            item.setFrequency(request.getFrequency());
        }

        if (request.getDurationDays() != null) {

            if (request.getDurationDays() <= 0) {
                throw new IllegalArgumentException(
                        "Sá»‘ ngĂ y sá»­ dá»¥ng pháº£i lá»›n hÆ¡n 0"
                );
            }

            item.setDurationDays(
                    request.getDurationDays()
            );
        }

        if (request.getQuantity() != null) {

            if (request.getQuantity()
                    .compareTo(BigDecimal.ZERO) <= 0) {

                throw new IllegalArgumentException(
                        "Sá»‘ lÆ°á»£ng thuá»‘c pháº£i lá»›n hÆ¡n 0"
                );
            }

            item.setQuantity(
                    request.getQuantity()
            );
        }

        if (request.getInstructions() != null) {
            item.setInstructions(
                    request.getInstructions()
            );
        }

        return prescriptionItemRepository.save(item);
    }

    /**
     * XĂ³a prescription item.
     * Chá»‰ doctor má»›i Ä‘Æ°á»£c xĂ³a.
     */
    @Transactional
    public void delete(Long id) {

        PrescriptionItem item = getById(id);

        assertDoctorRole();

        prescriptionItemRepository.delete(item);
    }

    /**
     * Kiá»ƒm tra tĂ i khoáº£n hiá»‡n táº¡i cĂ³ role DOCTOR.
     */
    private void assertDoctorRole() {

        if (!isDoctor()) {
            throw new IllegalArgumentException(
                    "Chá»‰ bĂ¡c sÄ© má»›i Ä‘Æ°á»£c thao tĂ¡c prescription item"
            );
        }
    }

    /**
     * Kiá»ƒm tra Ä‘Ă£ Ä‘Äƒng nháº­p.
     */
    private void assertAuthenticated() {

        var authentication =
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalArgumentException(
                    "NgÆ°á»i dĂ¹ng chÆ°a Ä‘Äƒng nháº­p"
            );
        }
    }

    /**
     * Kiá»ƒm tra role DOCTOR.
     */
    private boolean isDoctor() {

        var authentication =
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals("ROLE_DOCTOR")
                );
    }
}
