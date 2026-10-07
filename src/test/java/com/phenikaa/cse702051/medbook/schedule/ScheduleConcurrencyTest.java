package com.phenikaa.cse702051.medbook.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-03.3, 03.10: yêu cầu gửi song song thật (nhiều luồng, không dùng mock). Thay đổi lịch của một bác sĩ chạy lần
 * lượt nhờ khóa dòng bác sĩ; xóa ca và đặt lịch tranh cùng một slot thì khóa lạc quan của slot chỉ cho một bên thắng.
 */
class ScheduleConcurrencyTest extends AbstractScheduleTest {

    private static final int ROUNDS = 6;

    @Autowired
    private AppointmentRepository appointments;

    /** Chạy các yêu cầu cùng lúc (cùng chờ một hiệu lệnh) và trả mã HTTP theo đúng thứ tự đưa vào. */
    private static List<Integer> inParallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> create(String token, LocalDate day, String start, String end) {
        return () -> createSchedule(token, day, start, end, 30, "[]").andReturn().getResponse().getStatus();
    }

    private void assertNoDuplicateSlots(long doctorId, LocalDate day, int expected) {
        List<AppointmentSlot> daySlots = slots.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, day);
        Set<LocalTime> starts = new HashSet<>();
        daySlots.forEach(slot -> assertTrue(starts.add(slot.getStartTime()), "trùng slot " + slot.getStartTime()));
        assertEquals(expected, daySlots.size());
    }

    @Test
    @DisplayName("AC-03.3 Hai yêu cầu tạo ca chồng giờ gửi song song: đúng một 201, yêu cầu kia 422, không có slot trùng")
    void parallelOverlappingCreatesAdmitExactlyOne() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        for (int round = 0; round < ROUNDS; round++) {
            LocalDate day = freshDay();

            List<Integer> statuses = inParallel(List.of(
                    create(doctor.token(), day, "08:00", "10:00"),
                    create(doctor.token(), day, "09:00", "11:00")));

            assertEquals(List.of(201, 422), statuses.stream().sorted().toList(), "vòng " + round);
            assertEquals(1, schedules.findByDoctorIdAndWorkDate(doctor.doctorId(), day).size());
            assertNoDuplicateSlots(doctor.doctorId(), day, 4);
        }
    }

    @Test
    @DisplayName("AC-03.3 Năm yêu cầu tạo cùng một ca gửi song song: đúng một 201, còn lại 422, slot chỉ sinh một lần")
    void parallelIdenticalCreatesAdmitExactlyOne() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        LocalDate day = freshDay();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            calls.add(create(doctor.token(), day, "08:00", "10:00"));
        }

        List<Integer> statuses = inParallel(calls);

        assertEquals(1, statuses.stream().filter(code -> code == 201).count(), statuses.toString());
        assertEquals(4, statuses.stream().filter(code -> code == 422).count(), statuses.toString());
        assertEquals(1, schedules.findByDoctorIdAndWorkDate(doctor.doctorId(), day).size());
        assertNoDuplicateSlots(doctor.doctorId(), day, 4);
    }

    @Test
    @DisplayName("AC-03.3 Hai bác sĩ khác nhau tạo ca cùng ngày giờ song song: cả hai 201 (khóa theo từng bác sĩ)")
    void differentDoctorsDoNotBlockEachOther() throws Exception {
        IsolatedDoctor a = newDoctor();
        IsolatedDoctor b = newDoctor();
        LocalDate day = freshDay();

        List<Integer> statuses = inParallel(List.of(
                create(a.token(), day, "08:00", "10:00"),
                create(b.token(), day, "08:00", "10:00")));

        assertEquals(List.of(201, 201), statuses);
        assertNoDuplicateSlots(a.doctorId(), day, 4);
        assertNoDuplicateSlots(b.doctorId(), day, 4);
    }

    @Test
    @DisplayName("AC-03.10 Xóa ca song song với đặt đúng slot của ca: không cả hai cùng thắng, không lịch hẹn mồ côi, không 5xx")
    void deleteRacingWithBookingNeverOrphansAnAppointment() throws Exception {
        IsolatedDoctor doctor = newDoctor();
        int deletes = 0;
        int bookings = 0;
        for (int round = 0; round < ROUNDS; round++) {
            IsolatedPatient patient = data.isolatedPatient("race" + unique());
            LocalDate day = freshDay();
            long scheduleId = createOk(doctor.token(), day, "08:00", "08:30", 30, "[]");
            long slotId = firstFreeSlotId(doctor.doctorId(), day);

            List<Integer> statuses = inParallel(List.of(
                    () -> mvc.perform(delete(URL + "/" + scheduleId).header("Authorization", doctor.token()))
                            .andReturn().getResponse().getStatus(),
                    () -> book(patient.token(), slotId).andReturn().getResponse().getStatus()));
            int deleted = statuses.get(0);
            int booked = statuses.get(1);

            assertTrue(deleted == 204 || deleted == 409, "xóa ca: " + deleted);
            assertTrue(booked == 201 || booked == 404 || booked == 409, "đặt lịch: " + booked);
            assertFalse(deleted == 204 && booked == 201, "cả hai cùng thắng ở vòng " + round);

            boolean held = !appointments.findActiveBySlotIds(List.of(slotId), AppointmentStatus.CANCELLED).isEmpty();
            assertEquals(booked == 201, held, "lịch hẹn giữ chỗ phải khớp kết quả đặt lịch");
            if (booked == 201) {
                assertTrue(schedules.existsById(scheduleId), "đã có lịch hẹn thì ca phải còn");
                assertEquals("BOOKED", slots.findById(slotId).orElseThrow().getStatus());
                bookings++;
            }
            if (deleted == 204) {
                assertFalse(schedules.existsById(scheduleId));
                assertTrue(slots.findById(slotId).isEmpty(), "ca đã xóa thì slot chưa có lịch sử cũng bị xóa");
                deletes++;
            } else {
                assertTrue(schedules.existsById(scheduleId), "xóa bị từ chối thì ca giữ nguyên");
            }
        }
        assertTrue(deletes + bookings >= 1, "ít nhất một yêu cầu phải thành công trong " + ROUNDS + " vòng");
    }
}
