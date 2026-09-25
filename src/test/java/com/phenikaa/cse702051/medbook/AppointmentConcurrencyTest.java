package com.phenikaa.cse702051.medbook;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.service.AppointmentService;

@ExtendWith(MockitoExtension.class)
public class AppointmentConcurrencyTest {

    @InjectMocks
    private AppointmentService appointmentService;

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    private Long slotId = 1L;
    private Doctor doctor;

    @BeforeEach
    public void setUp() {
        doctor = Doctor.builder()
                .id(1L)
                .fullName("Bác sĩ Nguyễn Văn A")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Nghiệm thu Dev 3: 100 request đồng thời đặt 1 slot -> 1 thành công, 99 lỗi 409 Conflict")
    public void testConcurrentBooking_OnlyOneSucceeds() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(30);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicBoolean slotTaken = new AtomicBoolean(false);

        // Mô phỏng hành vi khóa lạc quan (Optimistic Lock) của CSDL
        when(slotRepository.findById(slotId)).thenAnswer(invocation -> {
            AppointmentSlot s = AppointmentSlot.builder()
                    .id(slotId)
                    .doctor(doctor)
                    .slotDate(LocalDate.now().plusDays(1))
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(9, 30))
                    .isAvailable(!slotTaken.get())
                    .status(slotTaken.get() ? "BOOKED" : "AVAILABLE")
                    .build();
            return Optional.of(s);
        });

        when(slotRepository.saveAndFlush(any(AppointmentSlot.class))).thenAnswer(invocation -> {
            // Chỉ duy nhất request đầu tiên đổi cờ thành công, 99 request sau ném lỗi xung đột phiên bản
            if (slotTaken.compareAndSet(false, true)) {
                return invocation.getArgument(0);
            } else {
                throw new OptimisticLockingFailureException("Optimistic lock conflict");
            }
        });

        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment a = invocation.getArgument(0);
            a.setId(999L);
            return a;
        });

        // Bắn 100 thread đồng thời
        for (int i = 0; i < numberOfThreads; i++) {
            final long patientId = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();
                    appointmentService.bookAppointment(patientId, slotId, "Khám định kỳ");
                    successCount.incrementAndGet();
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode().value() == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Phát lệnh chạy đồng loạt
        doneLatch.await();
        executorService.shutdown();

        // Tiêu chí nghiệm thu Dev 3
        assertEquals(1, successCount.get(), "Chỉ duy nhất 1 yêu cầu đặt lịch được thành công!");
        assertEquals(99, conflictCount.get(), "Chính xác 99 yêu cầu xung đột phải trả về mã 409 Conflict!");
    }
}