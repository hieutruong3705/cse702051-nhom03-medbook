package com.phenikaa.cse702051.medbook.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
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
import com.phenikaa.cse702051.medbook.model.Role;
import com.phenikaa.cse702051.medbook.model.SlotHold;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.model.UserRole;
import com.phenikaa.cse702051.medbook.model.UserRoleId;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.RoleRepository;
import com.phenikaa.cse702051.medbook.repository.SlotHoldRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.repository.UserRoleRepository;
import com.phenikaa.cse702051.medbook.service.AuditActions;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * Giữ chỗ dưới tải đồng thời. Các yêu cầu cùng xuất phát một lúc và chạy qua toàn bộ tầng controller,
 * service, repository trên CSDL thật của test, không dùng mock. Bảo đảm cần giữ: một khung giờ có tối đa một bản
 * giữ còn hạn, và một khung giờ đang bị giữ không bị người khác đặt mất.
 */
class SlotHoldConcurrencyTest extends AbstractApiTest {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SlotHoldConcurrencyTest.class);
    private static final int USERS = 50;

    @Autowired
    private SlotHoldRepository holds;
    @Autowired
    private AppointmentSlotRepository slots;
    @Autowired
    private AppointmentRepository appointments;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private UserRoleRepository userRoles;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toLowerCase();
    }

    private record Holder(Long userId, String token) {
    }

    /**
     * Tài khoản mới có đúng một vai trò, kèm token. Không qua {@code ApiTestData.user} vì 50 lần băm mật khẩu sẽ rất
     * chậm; các tài khoản này không đăng nhập bằng mật khẩu, token lấy thẳng từ phiên.
     */
    private Holder holder(String key, int index, String roleCode) {
        LocalDateTime now = LocalDateTime.now();
        String username = "hold_" + key + "_" + index;
        User user = users.save(User.builder()
                .username(username)
                .passwordHash("khong-dang-nhap-bang-mat-khau")
                .fullName("Người giữ chỗ thử nghiệm " + index)
                .email(username + "@example.com")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
        Role role = roles.findByCode(roleCode).orElseThrow();
        userRoles.save(UserRole.builder()
                .id(new UserRoleId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .createdAt(now)
                .build());
        return new Holder(user.getId(), bearer(loginSessions.create(user.getId()).token()));
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

    private int hold(String token, long slotId) throws Exception {
        return mvc.perform(post("/api/v1/appointment-slots/" + slotId + "/hold").header("Authorization", token))
                .andReturn().getResponse().getStatus();
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

    private List<SlotHold> holdsOn(long slotId) {
        return holds.findAll().stream().filter(h -> h.getSlotId().equals(slotId)).toList();
    }

    @Test
    @DisplayName("50 người dùng khác nhau cùng giữ một khung giờ: đúng 1 nhận 200, 49 nhận 409, đúng một dòng slot_holds, không lỗi 5xx")
    void fiftyUsersHoldingOneSlotOnlyOneSucceeds() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("shc" + key);
        AppointmentSlot slot = data.futureSlot(doctor.doctorId());
        List<Holder> holders = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            holders.add(holder(key, i, i % 5 == 0 ? "RECEPTIONIST" : "PATIENT"));
        }
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (Holder each : holders) {
            tasks.add(() -> hold(each.token(), slot.getId()));
        }

        long started = System.nanoTime();
        List<Integer> statuses = runConcurrently(tasks);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        LOG.info("{} yêu cầu giữ chỗ đồng thời trên một khung giờ: 200 x{}, 409 x{}, khác x{}, {} ms", USERS,
                count(statuses, 200), count(statuses, 409),
                statuses.stream().filter(s -> s != 200 && s != 409).count(), elapsedMs);

        assertEquals(1, count(statuses, 200), "chỉ một người giữ được: " + statuses);
        assertEquals(USERS - 1, count(statuses, 409), "những người còn lại phải nhận 409: " + statuses);
        assertEquals(0, statuses.stream().filter(s -> s >= 500).count(), "không được có lỗi 5xx: " + statuses);
        List<SlotHold> rows = holdsOn(slot.getId());
        assertEquals(1, rows.size(), "đúng một dòng giữ chỗ");
        Long winner = rows.get(0).getUserId();
        assertTrue(holders.stream().anyMatch(h -> h.userId().equals(winner)));
        assertEquals(1, auditsOf(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId()).size(),
                "chỉ lần giữ thành công được ghi nhật ký");
        assertEquals(1, countAudits(AuditActions.ENTITY_APPOINTMENT_SLOTS, slot.getId(), AuditActions.SLOT_HOLD,
                winner));
        assertEquals("AVAILABLE", slots.findById(slot.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Giữ chỗ và đặt lịch cùng lúc trên một khung giờ: đúng một bên thắng, bên thắng giữ thì không ai đặt được")
    void holdAndBookRaceHasExactlyOneWinner() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("shr" + key);
        IsolatedPatient booker = data.isolatedPatient("shr" + key);
        for (int round = 0; round < 5; round++) {
            AppointmentSlot slot = data.futureSlot(doctor.doctorId());
            Holder holder = holder(key, 100 + round, "PATIENT");
            List<Callable<Integer>> tasks = new ArrayList<>();
            tasks.add(() -> hold(holder.token(), slot.getId()));
            for (int i = 0; i < 10; i++) {
                tasks.add(() -> book(booker.token(), slot.getId()));
            }

            List<Integer> statuses = runConcurrently(tasks);

            int held = statuses.get(0) == 200 ? 1 : 0;
            long booked = statuses.subList(1, statuses.size()).stream().filter(s -> s == 201).count();
            assertEquals(1, held + booked, "đúng một yêu cầu chiếm được khung giờ: " + statuses);
            assertEquals(0, statuses.stream().filter(s -> s != 200 && s != 201 && s != 409).count(),
                    "bên thua phải nhận 409: " + statuses);
            assertEquals(booked, appointments.countActiveBySlotId(slot.getId()));
            assertEquals(held, holdsOn(slot.getId()).size(), "đặt được thì không còn dòng giữ; giữ được thì đúng một dòng");
            assertEquals(held == 1 ? "AVAILABLE" : "BOOKED", slots.findById(slot.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    @DisplayName("Một người bấm liên tiếp nhiều khung giờ cùng lúc: mọi yêu cầu 200 nhưng chỉ còn lại đúng một bản giữ")
    void oneUserHoldingManySlotsAtOnceKeepsOnlyOne() throws Exception {
        String key = unique();
        IsolatedDoctor doctor = data.isolatedDoctor("shm" + key);
        Holder holder = holder(key, 200, "PATIENT");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            AppointmentSlot slot = data.futureSlot(doctor.doctorId());
            tasks.add(() -> hold(holder.token(), slot.getId()));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertEquals(tasks.size(), count(statuses, 200), "khung giờ nào cũng trống nên đều giữ được: " + statuses);
        assertEquals(1, holds.findAll().stream().filter(h -> h.getUserId().equals(holder.userId())).count(),
                "mỗi người tối đa một bản giữ");
    }
}
