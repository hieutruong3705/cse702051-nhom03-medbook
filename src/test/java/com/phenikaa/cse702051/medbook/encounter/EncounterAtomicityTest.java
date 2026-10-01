package com.phenikaa.cse702051.medbook.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * BE-04 (mục 2): bắt đầu khám là một giao dịch. Nếu bước sau khi lịch đã chuyển IN_PROGRESS thất bại (ở đây:
 * tạo/lấy bệnh án), lịch phải trở lại BOOKED và không có lần khám nào được lưu. Cần spy nên chạy trong
 * context riêng.
 */
class EncounterAtomicityTest extends AbstractApiTest {

    @MockitoSpyBean
    private MedicalRecordService medicalRecordService;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private EncounterRepository encounters;

    @Test
    @DisplayName("Lỗi sau khi chuyển lịch sang IN_PROGRESS → rollback: lịch vẫn BOOKED, không có lần khám")
    void failureAfterStatusChangeRollsBackEverything() throws Exception {
        IsolatedDoctor doc = data.isolatedDoctor("atomic_doc");
        IsolatedPatient pat = data.isolatedPatient("atomic_pat");
        Appointment appointment = data.newAppointment(doc.doctorId(), pat.patientId(), AppointmentStatus.BOOKED);

        doThrow(new IllegalStateException("lỗi giả lập khi lấy bệnh án"))
                .when(medicalRecordService).getOrCreateByPatientId(anyLong());

        mvc.perform(post("/api/v1/encounters").header("Authorization", doc.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\":" + appointment.getId() + "}"))
                .andExpect(status().isInternalServerError());

        assertEquals(AppointmentStatus.BOOKED, appointments.findById(appointment.getId()).orElseThrow().getStatus());
        assertFalse(encounters.existsByAppointmentId(appointment.getId()));
    }
}
