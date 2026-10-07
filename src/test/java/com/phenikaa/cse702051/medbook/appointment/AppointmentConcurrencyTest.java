package com.phenikaa.cse702051.medbook.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData;

/**
 * BE-03 — tiêu chí nghiệm thu "chống đặt trùng": 100 yêu cầu cùng đặt một slot trống chỉ đúng MỘT
 * thành công, 99 yêu cầu còn lại nhận 409, không có lỗi 5xx. Chạy trên DB thật (H2) qua toàn bộ
 * tầng Controller → Service → Repository, KHÔNG dùng mock.
 */
class AppointmentConcurrencyTest extends AbstractApiTest {

    private static final int THREADS = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private AppointmentSlotRepository slots;

    /** Chạy đồng loạt các tác vụ (cùng bắt đầu một lúc) và trả về mã HTTP của từng tác vụ. */
    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS), "các luồng chưa sẵn sàng");
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(120, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return statuses;
    }

    private int book(String token, long slotId) throws Exception {
        return mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1}".formatted(slotId)))
                .andReturn().getResponse().getStatus();
    }

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    @Test
    @DisplayName("100 yêu cầu đồng thời đặt cùng một slot → đúng 1 thành công (201), 99 lần 409, 0 lỗi 5xx")
    void hundredConcurrentBookingsOnlyOneSucceeds() throws Exception {
        AppointmentSlot slot = data.futureSlot(ApiTestData.DOCTOR1_ID);
        String patient1 = patientToken();
        String patient2 = patient2Token();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String token = i % 2 == 0 ? patient1 : patient2;
            tasks.add(() -> book(token, slot.getId()));
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertEquals(1, count(statuses, 201), "chỉ một yêu cầu được thành công: " + statuses);
        assertEquals(THREADS - 1, count(statuses, 409), "99 yêu cầu còn lại phải nhận 409: " + statuses);
        assertEquals(0, statuses.stream().filter(s -> s >= 500).count(), "không được có lỗi 5xx: " + statuses);

        AppointmentSlot saved = slots.findById(slot.getId()).orElseThrow();
        assertEquals("BOOKED", saved.getStatus());
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()), "bất biến: một slot — một lịch đang hiệu lực");
        assertEquals(1, appointments.findAll().stream()
                .filter(a -> a.getSlot().getId().equals(slot.getId())).count(),
                "các yêu cầu thua không được để lại lịch hẹn nào");
    }

    @Test
    @DisplayName("Nhiều slot, mỗi slot nhiều người tranh: mỗi slot đúng 1 lịch, tổng thành công = số slot")
    void manySlotsEachWithContention() throws Exception {
        int slotCount = 5;
        int perSlot = 12;
        List<AppointmentSlot> targets = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            targets.add(data.futureSlot(ApiTestData.DOCTOR1_ID));
        }
        String patient1 = patientToken();
        String patient2 = patient2Token();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (AppointmentSlot target : targets) {
            for (int i = 0; i < perSlot; i++) {
                String token = i % 2 == 0 ? patient1 : patient2;
                tasks.add(() -> book(token, target.getId()));
            }
        }
        List<Integer> statuses = runConcurrently(tasks);

        assertEquals(slotCount, count(statuses, 201), "mỗi slot đúng một thành công: " + statuses);
        assertEquals(slotCount * (perSlot - 1), count(statuses, 409));
        assertEquals(0, statuses.stream().filter(s -> s >= 500).count());
        for (AppointmentSlot target : targets) {
            assertEquals(1, appointments.countActiveBySlotId(target.getId()));
        }
    }

    @Test
    @DisplayName("Người đổi lịch và người đặt mới tranh cùng một slot: đúng một bên thắng; bên thua giữ nguyên trạng")
    void rescheduleRacesWithNewBooking() throws Exception {
        AppointmentSlot original = data.futureSlot(ApiTestData.DOCTOR1_ID);
        AppointmentSlot contested = data.futureSlot(ApiTestData.DOCTOR1_ID);
        long appointmentId = JSON.readTree(mvc.perform(post("/api/v1/appointments")
                .header("Authorization", patientToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1}".formatted(original.getId())))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String patient1 = patientToken();
        String patient2 = patient2Token();
        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> mvc.perform(patch("/api/v1/appointments/" + appointmentId + "/reschedule")
                .header("Authorization", patient1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newSlotId\":%d}".formatted(contested.getId())))
                .andReturn().getResponse().getStatus());
        tasks.add(() -> book(patient2, contested.getId()));
        List<Integer> statuses = runConcurrently(tasks);

        boolean rescheduleWon = statuses.get(0) == 200;
        assertEquals(1, statuses.stream().filter(s -> s == 200 || s == 201).count(),
                "đúng một bên thắng: " + statuses);
        assertEquals(1, appointments.countActiveBySlotId(contested.getId()));

        Appointment mine = appointments.findById(appointmentId).orElseThrow();
        if (rescheduleWon) {
            assertEquals(contested.getId(), mine.getSlot().getId());
            assertEquals("AVAILABLE", slots.findById(original.getId()).orElseThrow().getStatus());
        } else {
            assertEquals(409, statuses.get(0));
            assertEquals(original.getId(), mine.getSlot().getId(), "đổi thất bại ⇒ lịch cũ giữ nguyên");
            assertEquals("BOOKED", slots.findById(original.getId()).orElseThrow().getStatus());
            assertEquals(1, appointments.countActiveBySlotId(original.getId()));
        }
    }

    @Test
    @DisplayName("Lớp bảo vệ thứ hai ở CSDL: không thể lưu hai lịch đang hiệu lực trên cùng slot; lịch đã hủy thì được")
    void databaseRejectsTwoActiveAppointmentsOnSameSlot() {
        AppointmentSlot slot = data.futureSlot(ApiTestData.DOCTOR1_ID);
        var doctor = slot.getDoctor();

        Appointment first = Appointment.builder().patientId(ApiTestData.PATIENT1_ID).doctor(doctor)
                .status(AppointmentStatus.BOOKED).build();
        first.assignSlot(slot);
        first = appointments.saveAndFlush(first);

        Appointment second = Appointment.builder().patientId(ApiTestData.PATIENT2_ID).doctor(doctor)
                .status(AppointmentStatus.BOOKED).build();
        second.assignSlot(slot);
        assertThrows(DataIntegrityViolationException.class, () -> appointments.saveAndFlush(second));

        first.cancel("test", java.time.LocalDateTime.now());
        appointments.saveAndFlush(first);

        Appointment third = Appointment.builder().patientId(ApiTestData.PATIENT2_ID).doctor(doctor)
                .status(AppointmentStatus.BOOKED).build();
        third.assignSlot(slot);
        appointments.saveAndFlush(third); // không ném lỗi: lịch cũ đã hủy nên slot đặt lại được
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()));
    }
}
