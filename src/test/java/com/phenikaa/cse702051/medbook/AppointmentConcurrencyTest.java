
package com.phenikaa.cse702051.medbook;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.model.User;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.AppointmentService;

@ExtendWith(MockitoExtension.class)
class AppointmentConcurrencyTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentSlotRepository appointmentSlotRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    private AppointmentService appointmentService;

    private final Long slotId = 1L;

    private Doctor doctor;

    private Patient patient;

    private User user;

    @BeforeEach
    void setUp() {
        doctor = Doctor.builder()
                .id(1L)
                .fullName("Bac si Nguyen Van A")
                .isActive(true)
                .build();

        user = User.builder()
                .id(1L)
                .username("concurrency-patient")
                .build();

        patient = Patient.builder()
                .id(1L)
                .user(user)
                .build();

        // Khoi tao service bang cac mock duoc chi dinh ro rang.
        appointmentService = new AppointmentService(
                appointmentRepository,
                appointmentSlotRepository,
                patientRepository,
                doctorRepository,
                userRepository
        );

        when(userRepository.findByUsername("concurrency-patient"))
                .thenReturn(Optional.of(user));

        when(patientRepository.findByUser_Id(1L))
                .thenReturn(Optional.of(patient));
    }

    @Test
    @DisplayName("100 yeu cau dat cung slot: 1 thanh cong, 99 Conflict")
    void testConcurrentBooking_OnlyOneSucceeds()
            throws InterruptedException {

        final int numberOfRequests = 100;

        ExecutorService executorService =
                Executors.newFixedThreadPool(30);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch =
                new CountDownLatch(numberOfRequests);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedErrorCount = new AtomicInteger();

        /*
         * Trang thai slot dung chung cho cac thread.
         * Day la mo phong trong bo nho, khong phai MySQL that.
         */
        AtomicBoolean slotTaken = new AtomicBoolean(false);

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        "concurrency-patient",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))
                );

        /*
         * Moi lan doc slot se tra ve trang thai hien tai.
         * Khi slot da duoc dat, cac request tiep theo nhan 409.
         */
        when(appointmentSlotRepository.findById(slotId))
                .thenAnswer(invocation -> {
                    boolean taken = slotTaken.get();

                    AppointmentSlot slot = AppointmentSlot.builder()
                            .id(slotId)
                            .doctor(doctor)
                            .slotDate(LocalDate.now().plusDays(1))
                            .startTime(LocalTime.of(9, 0))
                            .endTime(LocalTime.of(9, 30))
                            .isAvailable(!taken)
                            .status(taken ? "BOOKED" : "AVAILABLE")
                            .build();

                    return Optional.of(slot);
                });

        /*
         * Chi mot thread duoc phep chuyen slot tu trong sang da dat.
         * Cac thread thua cuoc nhan OptimisticLockingFailureException.
         */
        when(appointmentSlotRepository.saveAndFlush(
                any(AppointmentSlot.class)
        )).thenAnswer(invocation -> {
            AppointmentSlot slot = invocation.getArgument(0);

            if (slotTaken.compareAndSet(false, true)) {
                return slot;
            }

            throw new OptimisticLockingFailureException(
                    "Optimistic lock conflict"
            );
        });

        when(appointmentRepository.saveAndFlush(
                any(Appointment.class)
        )).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(999L);
            return appointment;
        });

        try {
            for (int i = 0; i < numberOfRequests; i++) {
                executorService.submit(() -> {
                    try {
                        startLatch.await();

                        appointmentService.bookAppointment(
                                slotId,
                                "Kham dinh ky",
                                authentication
                        );

                        successCount.incrementAndGet();

                    } catch (ResponseStatusException ex) {
                        if (ex.getStatusCode().value() == 409) {
                            conflictCount.incrementAndGet();
                        } else {
                            unexpectedErrorCount.incrementAndGet();
                            ex.printStackTrace();
                        }

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpectedErrorCount.incrementAndGet();

                    } catch (Exception ex) {
                        unexpectedErrorCount.incrementAndGet();
                        ex.printStackTrace();

                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Cho cac request bat dau gan nhu cung luc.
            startLatch.countDown();

            assertTrue(
                    doneLatch.await(30, TimeUnit.SECONDS),
                    "Cac request khong hoan thanh trong 30 giay"
            );

        } finally {
            executorService.shutdownNow();
        }

        assertEquals(
                1,
                successCount.get(),
                "Chi mot request duoc dat lich thanh cong"
        );

        assertEquals(
                99,
                conflictCount.get(),
                "99 request con lai phai nhan 409 Conflict"
        );

        assertEquals(
                0,
                unexpectedErrorCount.get(),
                "Khong duoc co exception ngoai du kien"
        );
    }
}
