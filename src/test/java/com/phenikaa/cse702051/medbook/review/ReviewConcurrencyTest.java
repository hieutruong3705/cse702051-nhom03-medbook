package com.phenikaa.cse702051.medbook.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.DoctorReviewRepository;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * QTNV-16: mỗi lịch hẹn chỉ có một đánh giá kể cả khi nhiều yêu cầu gửi cùng lúc (ví dụ bấm gửi nhiều lần). Các
 * yêu cầu đi qua đủ các tầng; {@code UNIQUE(appointment_id)} là lớp bảo vệ cuối và được dịch thành 409.
 */
class ReviewConcurrencyTest extends AbstractApiTest {

    private static final int REQUESTS = 10;

    @Autowired
    private DoctorReviewRepository reviews;

    @Test
    @DisplayName("10 yêu cầu đánh giá cùng một lịch gửi đồng thời: đúng 1 lần 201, 9 lần 409, không có 5xx, CSDL có 1 đánh giá")
    void onlyOneOfManyConcurrentReviewsWins() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        IsolatedDoctor doctor = data.isolatedDoctor("rvc" + key);
        IsolatedPatient patient = data.isolatedPatient("rvc" + key);
        Appointment visit = data.newAppointment(doctor.doctorId(), patient.patientId(), AppointmentStatus.COMPLETED);

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            int stars = 1 + i % 5;
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/v1/appointments/{id}/review", visit.getId())
                        .header("Authorization", patient.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + stars + ",\"comment\":\"Gửi đồng thời\"}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        Map<Integer, Integer> byStatus = new java.util.TreeMap<>();
        for (Future<Integer> result : results) {
            byStatus.merge(result.get(30, TimeUnit.SECONDS), 1, Integer::sum);
        }
        pool.shutdown();

        assertEquals(Map.of(201, 1, 409, REQUESTS - 1), byStatus);
        assertEquals(1, reviews.findAll().stream().filter(r -> r.getAppointmentId().equals(visit.getId())).count());
    }
}
