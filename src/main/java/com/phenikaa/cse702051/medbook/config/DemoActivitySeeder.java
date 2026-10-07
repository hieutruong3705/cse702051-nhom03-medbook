package com.phenikaa.cse702051.medbook.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Encounter;
import com.phenikaa.cse702051.medbook.model.Invoice;
import com.phenikaa.cse702051.medbook.model.InvoiceItem;
import com.phenikaa.cse702051.medbook.model.MedicalService;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.EncounterRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceItemRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.EncounterService;
import com.phenikaa.cse702051.medbook.service.InvoiceService;
import com.phenikaa.cse702051.medbook.service.MedicalRecordService;
import com.phenikaa.cse702051.medbook.util.ReferenceCodes;

/**
 * Nạp lịch khám, lần khám và hóa đơn mẫu của {@value #DAYS} ngày gần nhất cho các tài khoản demo, để ba báo cáo của
 * quản trị viên (lịch khám, doanh thu, dịch vụ) có số liệu và biểu đồ ngay sau khi dựng hệ thống.
 *
 * <ul>
 * <li>Bật bằng {@code medbook.seed.activity=true} (biến môi trường {@code MEDBOOK_SEED_ACTIVITY}); không đặt thì đi
 * theo {@code medbook.seed.demo}. Không chạy ở hồ sơ {@code prod}.</li>
 * <li>Chỉ nạp khi CSDL chưa có lịch hẹn, lần khám và hóa đơn nào, và chỉ gắn vào tài khoản demo
 * ({@code doctor1}, {@code doctor2}, {@code patient1}, {@code patient2}): không bao giờ trộn vào dữ liệu thật.</li>
 * <li>Số liệu sinh theo quy tắc cố định (không ngẫu nhiên) nên đếm tay đối chiếu được; tổng được ghi ra log.</li>
 * </ul>
 */
@Component
public class DemoActivitySeeder implements ApplicationRunner {

    /** Số ngày trước hôm nay được nạp dữ liệu; nằm trong khoảng mặc định 30 ngày của các báo cáo. */
    public static final int DAYS = 28;

    private static final Logger log = LoggerFactory.getLogger(DemoActivitySeeder.class);
    private static final List<String> DOCTOR_USERNAMES = List.of("doctor1", "doctor2");
    private static final List<String> PATIENT_USERNAMES = List.of("patient1", "patient2");
    private static final LocalTime FIRST_VISIT = LocalTime.of(8, 0);
    private static final int VISIT_MINUTES = 30;
    private static final BigDecimal DISCOUNT = new BigDecimal("20000.00");

    private final boolean enabled;
    private final boolean production;
    private final ObjectProvider<DemoSeedRunner> demoSeed;
    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final MedicalServiceRepository services;
    private final AppointmentSlotRepository slots;
    private final AppointmentRepository appointments;
    private final EncounterRepository encounters;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository invoiceItems;
    private final MedicalRecordService medicalRecords;

    @Autowired
    public DemoActivitySeeder(
            @Value("${medbook.seed.activity:${medbook.seed.demo:false}}") boolean enabled,
            Environment environment,
            ObjectProvider<DemoSeedRunner> demoSeed,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbc,
            UserRepository users,
            DoctorRepository doctors,
            PatientRepository patients,
            MedicalServiceRepository services,
            AppointmentSlotRepository slots,
            AppointmentRepository appointments,
            EncounterRepository encounters,
            InvoiceRepository invoices,
            InvoiceItemRepository invoiceItems,
            MedicalRecordService medicalRecords) {
        this.enabled = enabled;
        this.production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        this.demoSeed = demoSeed;
        this.transaction = new TransactionTemplate(transactionManager);
        this.jdbc = jdbc;
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.services = services;
        this.slots = slots;
        this.appointments = appointments;
        this.encounters = encounters;
        this.invoices = invoices;
        this.invoiceItems = invoiceItems;
        this.medicalRecords = medicalRecords;
    }

    /** Số liệu đã nạp, dùng để đối chiếu với báo cáo. */
    public record Summary(long appointments, long completed, long cancelled, long booked, long paidInvoices,
            long unpaidInvoices, long voidInvoices, BigDecimal collectedAmount, BigDecimal unpaidAmount) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (production) {
            log.warn("MEDBOOK_SEED_ACTIVITY bị bỏ qua ở hồ sơ prod: không nạp dữ liệu mẫu vào môi trường thật.");
            return;
        }
        // Tài khoản demo phải có trước; gọi lại là an toàn vì bộ nạp tài khoản tự bỏ qua khi CSDL đã có người dùng.
        demoSeed.ifAvailable(DemoSeedRunner::seedIfEmpty);
        seedIfEmpty(LocalDate.now());
    }

    /** Nạp dữ liệu mẫu nếu CSDL chưa có hoạt động nào. Trả về rỗng khi bỏ qua. */
    public Optional<Summary> seedIfEmpty(LocalDate today) {
        if (appointments.count() > 0 || encounters.count() > 0 || invoices.count() > 0) {
            log.info("CSDL đã có lịch hẹn, lần khám hoặc hóa đơn, không nạp dữ liệu mẫu cho báo cáo.");
            return Optional.empty();
        }
        return generate(today);
    }

    /** Sinh dữ liệu mẫu trong một giao dịch. Trả về rỗng khi thiếu tài khoản demo hoặc danh mục dịch vụ. */
    public Optional<Summary> generate(LocalDate today) {
        List<Doctor> demoDoctors = DOCTOR_USERNAMES.stream()
                .flatMap(name -> users.findByUsername(name).stream())
                .flatMap(user -> doctors.findByUserId(user.getId()).stream())
                .toList();
        List<Patient> demoPatients = PATIENT_USERNAMES.stream()
                .flatMap(name -> patients.findByUserUsername(name).stream())
                .toList();
        List<MedicalService> activeServices = services.findAll().stream()
                .filter(service -> "ACTIVE".equals(service.getStatus()) && service.getPrice() != null)
                .sorted(Comparator.comparing(MedicalService::getId))
                .toList();
        if (demoDoctors.isEmpty() || demoPatients.isEmpty() || activeServices.isEmpty()) {
            log.info("Thiếu tài khoản demo hoặc dịch vụ đang hoạt động, không nạp dữ liệu mẫu cho báo cáo.");
            return Optional.empty();
        }
        Summary summary = transaction.execute(status -> insert(today, demoDoctors, demoPatients, activeServices));
        log.info("Đã nạp dữ liệu mẫu cho báo cáo ({} ngày gần nhất): {}", DAYS, summary);
        return Optional.ofNullable(summary);
    }

    private Summary insert(LocalDate today, List<Doctor> demoDoctors, List<Patient> demoPatients,
            List<MedicalService> activeServices) {
        long completed = 0;
        long cancelled = 0;
        long booked = 0;
        long paid = 0;
        long unpaid = 0;
        long voided = 0;
        BigDecimal collectedAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal unpaidAmount = BigDecimal.ZERO.setScale(2);
        List<Object[]> bookedAt = new ArrayList<>();

        for (int back = DAYS; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            for (int d = 0; d < demoDoctors.size(); d++) {
                Doctor doctor = demoDoctors.get(d);
                Map<LocalTime, AppointmentSlot> existing = slots
                        .findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.getId(), day).stream()
                        .collect(Collectors.toMap(AppointmentSlot::getStartTime, Function.identity(), (a, b) -> a));
                int visits = 1 + (back + d) % 3;
                for (int v = 0; v < visits; v++) {
                    // n quyết định mọi lựa chọn của lượt khám này: cùng ngày chạy thì luôn ra cùng một bộ số liệu
                    int n = back * 7 + d * 3 + v;
                    LocalTime start = FIRST_VISIT.plusMinutes((long) VISIT_MINUTES * v);
                    AppointmentStatus outcome = n % 6 == 0 ? AppointmentStatus.CANCELLED
                            : n % 11 == 0 ? AppointmentStatus.BOOKED : AppointmentStatus.COMPLETED;
                    Patient patient = demoPatients.get(n % demoPatients.size());
                    MedicalService service = activeServices.get(n % activeServices.size());

                    AppointmentSlot slot = existing.get(start);
                    if (slot != null && appointments.existsAnyBySlotId(slot.getId())) {
                        continue;
                    }
                    if (slot == null) {
                        slot = AppointmentSlot.builder().doctor(doctor).slotDate(day).startTime(start)
                                .endTime(start.plusMinutes(VISIT_MINUTES)).build();
                    }
                    boolean held = outcome.holdsSlot();
                    slot.setIsAvailable(!held);
                    slot.setStatus(held ? "BOOKED" : "AVAILABLE");
                    slot = slots.save(slot);

                    Appointment appointment = Appointment.builder().patientId(patient.getId()).doctor(doctor)
                            .slot(slot).serviceId(service.getId()).status(AppointmentStatus.BOOKED)
                            .notes("Dữ liệu mẫu phục vụ báo cáo").build();
                    if (outcome == AppointmentStatus.CANCELLED) {
                        appointment.cancel("Bệnh nhân báo bận", day.minusDays(1).atTime(18, 0));
                    } else {
                        appointment.setStatus(outcome);
                    }
                    appointment = appointments.save(appointment);
                    bookedAt.add(new Object[] { Timestamp.valueOf(day.minusDays(3).atTime(9, 0).plusMinutes(n)),
                            Timestamp.valueOf(day.atTime(start)), appointment.getId() });

                    if (outcome == AppointmentStatus.CANCELLED) {
                        cancelled++;
                        continue;
                    }
                    if (outcome == AppointmentStatus.BOOKED) {
                        booked++;
                        continue;
                    }
                    completed++;
                    LocalDateTime visitAt = day.atTime(start);
                    saveEncounter(appointment, patient, doctor, visitAt);

                    String invoiceStatus = n % 13 == 0 ? InvoiceService.VOID
                            : n % 9 == 0 || back == 1 ? InvoiceService.UNPAID : InvoiceService.PAID;
                    List<MedicalService> lines = new ArrayList<>(List.of(service));
                    MedicalService extra = activeServices.get((n + 1) % activeServices.size());
                    if (n % 4 == 0 && !extra.getId().equals(service.getId())) {
                        lines.add(extra);
                    }
                    BigDecimal total = saveInvoice(appointment, patient, lines, n % 8 == 0, invoiceStatus,
                            visitAt.plusMinutes(VISIT_MINUTES));
                    if (InvoiceService.PAID.equals(invoiceStatus)) {
                        paid++;
                        collectedAmount = collectedAmount.add(total);
                    } else if (InvoiceService.UNPAID.equals(invoiceStatus)) {
                        unpaid++;
                        unpaidAmount = unpaidAmount.add(total);
                    } else {
                        voided++;
                    }
                }
            }
        }

        // created_at của lịch hẹn do @PrePersist đặt là "bây giờ"; sửa lại thành thời điểm đặt trước ngày khám
        appointments.flush();
        jdbc.batchUpdate("UPDATE appointments SET created_at = ?, updated_at = ? WHERE id = ?", bookedAt);
        return new Summary(completed + cancelled + booked, completed, cancelled, booked, paid, unpaid, voided,
                collectedAmount, unpaidAmount);
    }

    private void saveEncounter(Appointment appointment, Patient patient, Doctor doctor, LocalDateTime visitAt) {
        Encounter encounter = new Encounter();
        encounter.setMedicalRecordId(medicalRecords.getOrCreateByPatientId(patient.getId()).getId());
        encounter.setAppointmentId(appointment.getId());
        encounter.setDoctorId(doctor.getId());
        encounter.setEncounterAt(visitAt);
        encounter.setChiefComplaint("Khám theo lịch hẹn (dữ liệu mẫu)");
        encounter.setDiagnosis("Dữ liệu mẫu, không phải chẩn đoán thật");
        encounter.setStatus(EncounterService.COMPLETED);
        encounter.setCreatedAt(visitAt);
        encounter.setUpdatedAt(visitAt.plusMinutes(VISIT_MINUTES));
        encounters.save(encounter);
    }

    private BigDecimal saveInvoice(Appointment appointment, Patient patient, List<MedicalService> lines,
            boolean discounted, String status, LocalDateTime issuedAt) {
        BigDecimal subtotal = lines.stream().map(line -> money(line.getPrice()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal discount = discounted && subtotal.compareTo(DISCOUNT) > 0 ? DISCOUNT : BigDecimal.ZERO.setScale(2);
        BigDecimal total = subtotal.subtract(discount);

        Invoice invoice = new Invoice();
        invoice.setInvoiceCode(ReferenceCodes.next("INV", issuedAt.toLocalDate(), invoices::existsByInvoiceCode));
        invoice.setPatientId(patient.getId());
        invoice.setAppointmentId(appointment.getId());
        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discount);
        invoice.setTotalAmount(total);
        invoice.setStatus(status);
        invoice.setIssuedAt(issuedAt);
        invoice.setCreatedAt(issuedAt);
        invoice.setUpdatedAt(issuedAt.plusMinutes(10));
        if (InvoiceService.PAID.equals(status)) {
            invoice.setPaidAt(issuedAt.plusMinutes(10));
        } else if (InvoiceService.VOID.equals(status)) {
            invoice.setVoidedAt(issuedAt.plusMinutes(10));
            invoice.setVoidReason("Lập nhầm dịch vụ (dữ liệu mẫu)");
        }
        invoice = invoices.saveAndFlush(invoice);

        for (MedicalService line : lines) {
            InvoiceItem item = new InvoiceItem();
            item.setInvoiceId(invoice.getId());
            item.setServiceId(line.getId());
            item.setDescription(line.getName());
            item.setQuantity(BigDecimal.ONE);
            item.setUnitPrice(money(line.getPrice()));
            item.setLineTotal(money(line.getPrice()));
            item.setCreatedAt(issuedAt);
            invoiceItems.save(item);
        }
        return total;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
