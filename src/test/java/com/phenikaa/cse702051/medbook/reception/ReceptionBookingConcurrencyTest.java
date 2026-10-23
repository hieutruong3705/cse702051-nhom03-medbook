package com.phenikaa.cse702051.medbook.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Đặt hộ tại quầy giữ nguyên bảo đảm "một khung giờ, một lịch". 50 yêu cầu đặt hộ cùng lúc vào một khung
 * giờ chỉ một thành công; lễ tân và bệnh nhân cùng tranh một khung giờ cũng chỉ một bên thắng. Chạy qua toàn bộ
 * tầng controller, service, repository trên CSDL thật của test, không dùng mock.
 */
class ReceptionBookingConcurrencyTest extends AbstractApiTest {

    private static final int REQUESTS = 50;

    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private AppointmentSlotRepository slots;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

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

    private int bookAtDesk(String token, long patientId, long slotId) throws Exception {
        return mvc.perform(post("/api/v1/reception/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":%d,\"slotId\":%d,\"serviceId\":1}".formatted(patientId, slotId)))
                .andReturn().getResponse().getStatus();
    }

    private int bookAsPatient(String token, long slotId) throws Exception {
        return mvc.perform(post("/api/v1/appointments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":%d,\"serviceId\":1}".formatted(slotId)))
                .andReturn().getResponse().getStatus();
    }

    private static long count(List<Integer> statuses, int status) {
        return statuses.stream().filter(s -> s == status).count();
    }

    private void assertExactlyOneWinner(List<Integer> statuses, AppointmentSlot slot) {
        assertEquals(1, count(statuses, 201), "chỉ một yêu cầu được thành công: " + statuses);
        assertEquals(statuses.size() - 1, count(statuses, 409), "các yêu cầu còn lại phải nhận 409: " + statuses);
        assertEquals(0, statuses.stream().filter(s -> s >= 500).count(), "không được có lỗi 5xx: " + statuses);
        assertEquals("BOOKED", slots.findById(slot.getId()).orElseThrow().getStatus());
        assertEquals(1, appointments.countActiveBySlotId(slot.getId()), "một khung giờ, một lịch đang hiệu lực");
        assertEquals(1, appointments.findAll().stream()
                .filter(a -> a.getSlot().getId().equals(slot.getId())).count(),
                "các yêu cầu thua không được để lại lịch hẹn nào");
    }

    @Test
    @DisplayName("50 yêu cầu đặt hộ đồng thời vào một khung giờ: đúng 1 trả 201, 49 trả 409, đúng một lịch hẹn")
    void fiftyConcurrentDeskBookingsOnlyOneSucceeds() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rbc" + key);
        long patientA = data.isolatedPatient("rbca" + key).patientId();
        long patientB = data.isolatedPatient("rbcb" + key).patientId();
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());
        String deskOne = receptionistToken();
        String deskTwo = data.isolatedReceptionist("rbc" + key).token();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            String token = i % 2 == 0 ? deskOne : deskTwo;
            long patientId = i % 2 == 0 ? patientA : patientB;
            tasks.add(() -> bookAtDesk(token, patientId, slot.getId()));
        }

        assertExactlyOneWinner(runConcurrently(tasks), slot);
    }

    @Test
    @DisplayName("Lễ tân và bệnh nhân cùng đặt một khung giờ đồng thời: đúng một bên 201, bên kia 409")
    void deskAndPatientRaceForTheSameSlot() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("rbm" + key);
        IsolatedPatient online = data.isolatedPatient("rbmo" + key);
        long walkIn = data.isolatedPatient("rbmw" + key).patientId();
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());
        String desk = receptionistToken();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> bookAtDesk(desk, walkIn, slot.getId()));
            tasks.add(() -> bookAsPatient(online.token(), slot.getId()));
        }

        assertExactlyOneWinner(runConcurrently(tasks), slot);
    }
}
