package com.phenikaa.cse702051.medbook.patient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.service.DoctorScopePolicy;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData;

/** BE-02: quy tắc "bác sĩ phụ trách bệnh nhân" (D7). */
class DoctorScopeServiceTest extends AbstractApiTest {

    @Autowired
    private DoctorScopePolicy scope;

    private Patient withActiveAppointment;
    private Patient withCancelledOnly;
    private Patient withEncounterOnly;
    private Patient unrelated;

    @BeforeEach
    void fixtures() {
        withActiveAppointment = data.extraPatient("SCOPE-ACTIVE");
        data.appointment(ApiTestData.DOCTOR1_ID, withActiveAppointment.getId(), AppointmentStatus.BOOKED);

        withCancelledOnly = data.extraPatient("SCOPE-CANCELLED");
        data.appointment(ApiTestData.DOCTOR1_ID, withCancelledOnly.getId(), AppointmentStatus.CANCELLED);

        withEncounterOnly = data.extraPatient("SCOPE-ENCOUNTER");
        data.encounter(ApiTestData.DOCTOR1_ID, data.record(withEncounterOnly.getId()).getId());

        unrelated = data.extraPatient("SCOPE-NONE");
    }

    @Test
    @DisplayName("Có lịch hẹn chưa hủy → phụ trách")
    void activeAppointmentMakesDoctorResponsible() {
        assertTrue(scope.isResponsible(ApiTestData.DOCTOR1_ID, withActiveAppointment.getId()));
    }

    @Test
    @DisplayName("Mọi trạng thái lịch khác CANCELLED (kể cả đang khám, hoàn thành) đều tính là phụ trách")
    void nonCancelledStatusesCount() {
        Patient inProgress = data.extraPatient("SCOPE-INPROGRESS");
        data.appointment(ApiTestData.DOCTOR1_ID, inProgress.getId(), AppointmentStatus.IN_PROGRESS);
        Patient completed = data.extraPatient("SCOPE-COMPLETED");
        data.appointment(ApiTestData.DOCTOR1_ID, completed.getId(), AppointmentStatus.COMPLETED);

        assertTrue(scope.isResponsible(ApiTestData.DOCTOR1_ID, inProgress.getId()));
        assertTrue(scope.isResponsible(ApiTestData.DOCTOR1_ID, completed.getId()));
    }

    @Test
    @DisplayName("Chỉ có lịch hẹn đã hủy → không phụ trách")
    void cancelledOnlyIsNotResponsible() {
        assertFalse(scope.isResponsible(ApiTestData.DOCTOR1_ID, withCancelledOnly.getId()));
    }

    @Test
    @DisplayName("Có lần khám (không cần lịch hẹn) → phụ trách")
    void encounterMakesDoctorResponsible() {
        assertTrue(scope.isResponsible(ApiTestData.DOCTOR1_ID, withEncounterOnly.getId()));
    }

    @Test
    @DisplayName("Bác sĩ khác hoặc bệnh nhân không liên quan → không phụ trách")
    void unrelatedIsNotResponsible() {
        assertFalse(scope.isResponsible(ApiTestData.DOCTOR2_ID, withActiveAppointment.getId()));
        assertFalse(scope.isResponsible(ApiTestData.DOCTOR2_ID, withEncounterOnly.getId()));
        assertFalse(scope.isResponsible(ApiTestData.DOCTOR1_ID, unrelated.getId()));
    }

    @Test
    @DisplayName("Tham số null hoặc ID không tồn tại → không phụ trách (không ném lỗi)")
    void nullAndUnknownAreSafe() {
        assertFalse(scope.isResponsible(null, withActiveAppointment.getId()));
        assertFalse(scope.isResponsible(ApiTestData.DOCTOR1_ID, null));
        assertFalse(scope.isResponsible(999999L, 999999L));
    }
}
