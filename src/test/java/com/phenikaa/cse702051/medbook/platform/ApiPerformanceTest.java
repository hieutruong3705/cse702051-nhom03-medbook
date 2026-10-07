package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.phenikaa.cse702051.medbook.config.migration.V2__CxAdditiveSchema;
import com.phenikaa.cse702051.medbook.service.PublicCatalogCache;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * AC-09.6 và sổ tay Buổi 9: đo thời gian phản hồi của các điểm cuối đọc nhiều nhất với hơn 5.000 bản ghi ở mỗi bảng
 * chính, TRƯỚC và SAU tối ưu (chỉ mục của migration V2 và bộ nhớ đệm danh mục). Mỗi điểm cuối được gọi 50 lần, lấy
 * trung vị và phân vị 95. Kết quả ghi ra {@code target/performance-report.md}.
 *
 * <p>Gắn thẻ {@code perf} nên không chạy trong {@code mvnw test} thường. Chạy riêng:
 * {@code mvnw test "-Dmedbook.test.excludedGroups=" "-Dgroups=perf"}. Phép đo chạy trong tiến trình (MockMvc, H2
 * trong bộ nhớ) nên phản ánh chi phí của ứng dụng và truy vấn, không gồm độ trễ mạng.
 */
@Tag("perf")
@TestPropertySource(properties = "medbook.cache.enabled=true")
class ApiPerformanceTest extends AbstractApiTest {

    private static final int RUNS = 50;
    private static final int WARM_UP = 8;
    private static final int DOCTORS = 200;
    private static final int PATIENTS = 500;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PublicCatalogCache publicCatalogCache;

    private record Stats(double medianMs, double p95Ms) {
    }

    private record Endpoint(String name, Supplier<MockHttpServletRequestBuilder> request, boolean evictCacheFirst) {
    }

    private Stats measure(Endpoint endpoint, boolean bypassCache) throws Exception {
        for (int i = 0; i < WARM_UP; i++) {
            assertEquals(200, mvc.perform(endpoint.request().get()).andReturn().getResponse().getStatus(),
                    endpoint.name());
        }
        double[] elapsed = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            if (bypassCache && endpoint.evictCacheFirst()) {
                publicCatalogCache.evictAll();
            }
            long start = System.nanoTime();
            mvc.perform(endpoint.request().get()).andReturn();
            elapsed[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(elapsed);
        return new Stats((elapsed[RUNS / 2 - 1] + elapsed[RUNS / 2]) / 2, elapsed[(int) Math.ceil(RUNS * 0.95) - 1]);
    }

    /** Nạp dữ liệu khối lượng lớn bằng lệnh INSERT ... SELECT (nhanh hơn nhiều so với đi qua API). */
    private long[] loadData(String key, long heavyPatientId) {
        String prefix = "pfdoc_" + key + "_";
        jdbc.update("""
                insert into users (username, password_hash, full_name, email, status, failed_login_count,
                                   token_version, created_at, updated_at)
                select ? || x, 'khong-dang-nhap-duoc', 'Bác sĩ hiệu năng ' || x, ? || x || '@perf.local', 'ACTIVE',
                       0, 0, now(), now()
                from system_range(1, ?)
                """, prefix, prefix, DOCTORS);
        jdbc.update("""
                insert into doctors (user_id, specialty_id, full_name, license_number, is_active, created_at, updated_at)
                select id, 1 + mod(id, 5), full_name, 'PF-' || username, true, now(), now()
                from users where username like ?
                """, prefix + "%");
        jdbc.update("""
                insert into patients (patient_code, full_name, date_of_birth, gender_code, phone, status, created_at,
                                      updated_at)
                select 'PF' || ? || x, 'Bệnh nhân hiệu năng ' || x, date '1990-01-01', 'OTHER', '0900000000', 'ACTIVE',
                       now(), now()
                from system_range(1, ?)
                """, key, PATIENTS);
        long firstPatient = jdbc.queryForObject("select min(id) from patients where patient_code like ?", Long.class,
                "PF" + key + "%");

        // 200 bác sĩ x 30 slot (3 ngày x 10 slot 30 phút) = 6.000 slot ở tương lai xa
        jdbc.update("""
                insert into appointment_slots (doctor_id, slot_date, start_time, end_time, is_available, status,
                                               version, created_at, updated_at)
                select d.id, dateadd('DAY', 3000 + n.x / 10, current_date),
                       dateadd('MINUTE', 30 * mod(n.x, 10), time '08:00:00'),
                       dateadd('MINUTE', 30 * mod(n.x, 10) + 30, time '08:00:00'), true, 'AVAILABLE', 0, now(), now()
                from doctors d cross join system_range(0, 29) n
                where d.license_number like ?
                """, "PF-" + prefix + "%");
        // 5.000 lịch hẹn: 400 của một bệnh nhân (để đo "lịch của tôi"), còn lại chia cho 500 bệnh nhân
        jdbc.update("""
                insert into appointments (patient_id, doctor_id, slot_id, active_slot_id, service_id, status,
                                          created_at, updated_at)
                select case when rn <= 400 then ? else ? + mod(rn, ?) end, doctor_id, id, id, 1, 'BOOKED', now(), now()
                from (select s.id, s.doctor_id, row_number() over (order by s.id) rn
                      from appointment_slots s join doctors d on d.id = s.doctor_id
                      where d.license_number like ?) t
                where rn <= 5000
                """, heavyPatientId, firstPatient, PATIENTS, "PF-" + prefix + "%");
        jdbc.update("""
                update appointment_slots set is_available = false, status = 'BOOKED'
                where id in (select slot_id from appointments)
                """);
        jdbc.update("""
                insert into invoices (invoice_code, patient_id, subtotal, discount_amount, total_amount, status,
                                      issued_at, created_at, updated_at)
                select 'PF-' || ? || '-' || x, ? + mod(x, ?), 200000, 0, 200000,
                       case mod(x, 3) when 0 then 'PAID' when 1 then 'UNPAID' else 'VOID' end,
                       dateadd('MINUTE', -x, now()), now(), now()
                from system_range(1, 5000)
                """, key, firstPatient, PATIENTS);
        jdbc.update("""
                insert into audit_logs (action_code, entity_type, entity_id, created_at)
                select case mod(x, 4) when 0 then 'PERF_READ' when 1 then 'PERF_WRITE' when 2 then 'PERF_LOGIN'
                       else 'PERF_EXPORT' end, 'perf', x, dateadd('SECOND', -x, now())
                from system_range(1, 6000)
                """);
        long doctorWithFreeSlots = jdbc.queryForObject("""
                select max(s.doctor_id) from appointment_slots s join doctors d on d.id = s.doctor_id
                where d.license_number like ? and s.status = 'AVAILABLE'
                """, Long.class, "PF-" + prefix + "%");
        return new long[] { doctorWithFreeSlots };
    }

    /** Chạy migration V2 trên CSDL của test để tạo đúng các chỉ mục mà môi trường MySQL có. */
    private void applyIndexes() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            new V2__CxAdditiveSchema().migrate(new Context() {
                @Override
                public Configuration getConfiguration() {
                    return null;
                }

                @Override
                public Connection getConnection() {
                    return connection;
                }
            });
        }
    }

    @Test
    @DisplayName("AC-09.6 Hơn 5.000 bản ghi: trung vị của 50 yêu cầu dưới 400 ms; tìm bác sĩ và giờ trống có p95 dưới 1 giây")
    void measuresHotEndpointsBeforeAndAfterOptimisation() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        IsolatedPatient heavy = data.isolatedPatient("perf" + key);
        long doctorId = loadData(key, heavy.patientId())[0];
        LocalDate slotDay = LocalDate.now().plusDays(3002);
        String admin = adminToken();

        Map<String, Long> volumes = new LinkedHashMap<>();
        for (String table : List.of("doctors", "patients", "appointment_slots", "appointments", "invoices",
                "audit_logs")) {
            volumes.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        assertTrue(volumes.get("appointments") >= 5000 && volumes.get("invoices") >= 5000
                && volumes.get("audit_logs") >= 5000 && volumes.get("appointment_slots") >= 5000, volumes.toString());

        List<Endpoint> endpoints = List.of(
                new Endpoint("GET /doctors?keyword=hiệu năng&specialtyId=3 (tìm bác sĩ)",
                        () -> get("/api/v1/doctors").param("keyword", "hiệu năng").param("specialtyId", "3")
                                .param("size", "20"), false),
                new Endpoint("GET /doctors/{id}/slots?date= (giờ trống)",
                        () -> get("/api/v1/doctors/" + doctorId + "/slots").param("date", slotDay.toString()), false),
                new Endpoint("GET /appointments/me (lịch của tôi, 400 lịch)",
                        () -> get("/api/v1/appointments/me").param("size", "20").header("Authorization", heavy.token()),
                        false),
                new Endpoint("GET /admin/invoices?status=UNPAID (danh sách hóa đơn)",
                        () -> get("/api/v1/admin/invoices").param("status", "UNPAID").param("size", "20")
                                .header("Authorization", admin), false),
                new Endpoint("GET /admin/audit-logs?actionCode= (nhật ký hệ thống)",
                        () -> get("/api/v1/admin/audit-logs").param("actionCode", "PERF_LOGIN").param("size", "20")
                                .header("Authorization", admin), false),
                new Endpoint("GET /admin/reports/appointments?groupBy=DOCTOR (báo cáo)",
                        () -> get("/api/v1/admin/reports/appointments").param("groupBy", "DOCTOR")
                                .param("from", slotDay.minusDays(5).toString())
                                .param("to", slotDay.plusDays(5).toString()).header("Authorization", admin), false),
                new Endpoint("GET /specialties?size=100 (danh mục công khai)",
                        () -> get("/api/v1/specialties").param("size", "100"), true));

        Map<String, Stats> before = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints) {
            before.put(endpoint.name(), measure(endpoint, true)); // chưa có chỉ mục V2, không dùng bộ nhớ đệm
        }
        applyIndexes();
        Map<String, Stats> after = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints) {
            after.put(endpoint.name(), measure(endpoint, false));
        }

        List<String> report = new ArrayList<>();
        report.add("# Kết quả đo hiệu năng (sinh tự động)");
        report.add("");
        report.add("- Thời điểm: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        report.add("- Mỗi điểm cuối: " + WARM_UP + " lần làm nóng, " + RUNS + " lần đo; MockMvc trong tiến trình, H2.");
        report.add("- Số bản ghi: " + volumes);
        report.add("");
        report.add("| Điểm cuối | Trung vị trước (ms) | p95 trước (ms) | Trung vị sau (ms) | p95 sau (ms) |");
        report.add("|---|---:|---:|---:|---:|");
        for (Endpoint endpoint : endpoints) {
            Stats b = before.get(endpoint.name());
            Stats a = after.get(endpoint.name());
            report.add("| %s | %.2f | %.2f | %.2f | %.2f |".formatted(endpoint.name(), b.medianMs(), b.p95Ms(),
                    a.medianMs(), a.p95Ms()));
        }
        Path target = Path.of("target", "performance-report.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, String.join(System.lineSeparator(), report) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        report.forEach(System.out::println);

        for (Endpoint endpoint : endpoints) {
            Stats stats = after.get(endpoint.name());
            assertTrue(stats.medianMs() < 400, endpoint.name() + ": trung vị " + stats.medianMs() + " ms");
        }
        assertTrue(after.get(endpoints.get(0).name()).p95Ms() < 1000, "tìm bác sĩ: p95 phải dưới 1 giây");
        assertTrue(after.get(endpoints.get(1).name()).p95Ms() < 1000, "giờ trống: p95 phải dưới 1 giây");
    }
}
