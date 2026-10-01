package com.phenikaa.cse702051.medbook.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * BE-00: ma trận vai trò × route của {@code /api/v1}.
 * Với route "cho qua" ở tầng route nhưng service tự quyết (ví dụ bệnh án), test
 * khẳng định KHÔNG bị 401/403 chặn ở tầng route (dữ liệu seed chưa có bệnh án
 * nên nhận 404).
 */
class SecurityRoutingTest extends AbstractApiTest {

    private static final String NONE = "NONE";
    private static final String PATIENT = "PATIENT";
    private static final String DOCTOR = "DOCTOR";
    private static final String ADMIN = "ADMIN";

    static Stream<Arguments> routeMatrix() {
        return Stream.of(
                // --- Admin ---
                Arguments.of(NONE, "GET", "/api/v1/admin/invoices", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/admin/invoices", 403),
                Arguments.of(DOCTOR, "GET", "/api/v1/admin/invoices", 403),
                Arguments.of(ADMIN, "GET", "/api/v1/admin/invoices", 200),
                Arguments.of(PATIENT, "GET", "/api/v1/admin/data-metrics", 403),
                Arguments.of(PATIENT, "GET", "/api/v1/audit-logs", 403),
                Arguments.of(NONE, "GET", "/api/v1/audit-logs", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/appointments/admin/reports", 403),
                Arguments.of(DOCTOR, "GET", "/api/v1/appointments/admin/reports", 403),
                Arguments.of(ADMIN, "GET", "/api/v1/appointments/admin/reports", 200),
                Arguments.of(PATIENT, "GET", "/api/v1/system/metrics", 403),

                // --- Ghi vào hồ sơ bác sĩ/danh mục (đường dẫn cũ) chỉ Admin ---
                Arguments.of(PATIENT, "POST", "/api/v1/doctors/admin", 403),
                Arguments.of(DOCTOR, "POST", "/api/v1/doctors/admin", 403),
                Arguments.of(PATIENT, "PUT", "/api/v1/doctors/admin/1", 403),
                Arguments.of(PATIENT, "DELETE", "/api/v1/doctors/admin/1", 403),
                Arguments.of(NONE, "POST", "/api/v1/doctors/admin", 401),

                // --- Hóa đơn: bệnh nhân/bác sĩ không được thu tiền/hủy ---
                Arguments.of(PATIENT, "PUT", "/api/v1/invoices/1/pay", 403),
                Arguments.of(DOCTOR, "PUT", "/api/v1/invoices/1/pay", 403),
                Arguments.of(PATIENT, "PUT", "/api/v1/invoices/1/void", 403),

                // --- Lịch hẹn ---
                Arguments.of(DOCTOR, "POST", "/api/v1/appointments?patientId=4&slotId=1", 403),
                Arguments.of(ADMIN, "POST", "/api/v1/appointments?patientId=4&slotId=1", 403),
                Arguments.of(NONE, "POST", "/api/v1/appointments?patientId=4&slotId=1", 401),
                Arguments.of(PATIENT, "POST", "/api/v1/appointments", 400), // qua route, thiếu tham số
                Arguments.of(PATIENT, "PATCH", "/api/v1/appointments/1/status?status=COMPLETED&doctorId=1", 403),
                Arguments.of(ADMIN, "PATCH", "/api/v1/appointments/1/status?status=COMPLETED&doctorId=1", 403),
                Arguments.of(DOCTOR, "PATCH", "/api/v1/appointments/1/cancel?patientId=4", 403),
                Arguments.of(DOCTOR, "PATCH", "/api/v1/appointments/1/reschedule?newSlotId=2&patientId=4", 403),
                Arguments.of(NONE, "GET", "/api/v1/appointments/1", 401),

                // --- Bác sĩ ---
                Arguments.of(PATIENT, "GET", "/api/v1/doctor-schedules", 403),
                Arguments.of(ADMIN, "GET", "/api/v1/doctor-schedules", 403),
                Arguments.of(NONE, "GET", "/api/v1/doctor-schedules", 401),
                Arguments.of(DOCTOR, "GET", "/api/v1/doctor-schedules", 200),
                Arguments.of(DOCTOR, "GET", "/api/v1/doctor-schedules/slots", 200),
                Arguments.of(PATIENT, "POST", "/api/v1/doctor-schedules", 403),
                Arguments.of(ADMIN, "DELETE", "/api/v1/doctor-schedules/1", 403),
                Arguments.of(PATIENT, "GET", "/api/v1/schedule-breaks", 403),
                Arguments.of(NONE, "GET", "/api/v1/medicines", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/medicines", 403),
                Arguments.of(DOCTOR, "GET", "/api/v1/medicines", 404),
                Arguments.of(ADMIN, "GET", "/api/v1/medicines", 404),

                // --- Bệnh nhân ---
                Arguments.of(NONE, "GET", "/api/v1/patients/me", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/patients/me", 200),
                Arguments.of(DOCTOR, "GET", "/api/v1/patients/me", 403),
                Arguments.of(ADMIN, "GET", "/api/v1/patients/me", 403),
                Arguments.of(PATIENT, "GET", "/api/v1/patients", 403),
                Arguments.of(DOCTOR, "GET", "/api/v1/patients", 200),
                Arguments.of(ADMIN, "GET", "/api/v1/patients", 200),
                Arguments.of(PATIENT, "GET", "/api/v1/patients/1", 403),
                Arguments.of(PATIENT, "GET", "/api/v1/patients/1/medical-records", 403),

                // --- Bệnh án: route cho mọi người đã đăng nhập, service quyết định ---
                // (999999: ID không tồn tại, để test không phụ thuộc dữ liệu do test khác tạo)
                Arguments.of(NONE, "GET", "/api/v1/medical-records/999999", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/medical-records/999999", 404),
                Arguments.of(DOCTOR, "GET", "/api/v1/medical-records/999999", 404),
                Arguments.of(ADMIN, "GET", "/api/v1/medical-records/999999", 404),
                Arguments.of(NONE, "GET", "/api/v1/medical-records/me", 401),
                Arguments.of(DOCTOR, "GET", "/api/v1/medical-records/me", 403),

                // --- Dữ liệu khám: bệnh nhân phải qua được route (service kiểm chủ sở hữu) ---
                Arguments.of(NONE, "GET", "/api/v1/encounters/1", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/encounters/999999", 404), // qua route; service báo không tìm thấy
                Arguments.of(ADMIN, "GET", "/api/v1/encounters/999999", 404),
                Arguments.of(PATIENT, "GET", "/api/v1/prescriptions/999999", 404), // qua route; service báo không tìm thấy
                Arguments.of(NONE, "GET", "/api/v1/encounters/1/invoice", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/encounters/999999/invoice", 404),
                Arguments.of(ADMIN, "GET", "/api/v1/encounters/999999/invoice", 404),

                // --- Lần khám/tệp: chỉ bác sĩ bắt đầu, sửa, tải lên, xóa, tra cứu; chỉ bệnh nhân xem lịch sử của mình ---
                Arguments.of(PATIENT, "POST", "/api/v1/encounters", 403),
                Arguments.of(ADMIN, "POST", "/api/v1/encounters", 403),
                Arguments.of(NONE, "POST", "/api/v1/encounters", 401),
                Arguments.of(DOCTOR, "POST", "/api/v1/encounters", 400), // qua route; thiếu appointmentId bị validate
                Arguments.of(PATIENT, "PUT", "/api/v1/encounters/1", 403),
                Arguments.of(ADMIN, "PUT", "/api/v1/encounters/1", 403),
                Arguments.of(PATIENT, "GET", "/api/v1/encounters?appointmentId=1", 403),
                Arguments.of(ADMIN, "GET", "/api/v1/encounters?appointmentId=1", 403),
                Arguments.of(DOCTOR, "GET", "/api/v1/encounters", 400), // qua route; thiếu bộ lọc
                Arguments.of(DOCTOR, "GET", "/api/v1/encounters/me", 403),
                Arguments.of(ADMIN, "GET", "/api/v1/encounters/me", 403),
                Arguments.of(NONE, "GET", "/api/v1/encounters/me", 401),
                Arguments.of(PATIENT, "DELETE", "/api/v1/attachments/1", 403),
                Arguments.of(ADMIN, "DELETE", "/api/v1/attachments/1", 403),
                Arguments.of(NONE, "DELETE", "/api/v1/attachments/1", 401),
                Arguments.of(NONE, "GET", "/api/v1/attachments/1/download", 401),
                Arguments.of(ADMIN, "GET", "/api/v1/attachments/999999/download", 404),
                Arguments.of(NONE, "GET", "/api/v1/invoices/me", 401),

                // --- Công khai ---
                Arguments.of(NONE, "GET", "/api/v1/doctors", 200),
                Arguments.of(NONE, "GET", "/api/v1/doctors/1/slots", 200), // giờ trống của bác sĩ: công khai
                Arguments.of(NONE, "GET", "/api/v1/doctors/999999/slots", 404),
                Arguments.of(NONE, "GET", "/api/v1/appointment-slots/available?doctorId=1", 401), // đường dẫn cũ cần đăng nhập
                Arguments.of(PATIENT, "GET", "/api/v1/appointment-slots/available?doctorId=1", 200),
                Arguments.of(NONE, "GET", "/api/v1/specialties", 404), // công khai, controller chưa có endpoint
                Arguments.of(NONE, "GET", "/api/v1/medical-services", 200), // danh mục dịch vụ ACTIVE công khai
                Arguments.of(NONE, "GET", "/api/v1/system/status", 200),
                Arguments.of(NONE, "POST", "/api/v1/auth/login", 400), // công khai, body rỗng bị validate
                Arguments.of(NONE, "POST", "/api/v1/auth/register", 400),
                Arguments.of(NONE, "POST", "/api/v1/auth/logout", 401),

                // --- Route chưa khai báo: mặc định phải đăng nhập ---
                Arguments.of(NONE, "GET", "/api/v1/khong-ton-tai", 401),
                Arguments.of(PATIENT, "GET", "/api/v1/khong-ton-tai", 404));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("routeMatrix")
    @DisplayName("Ma trận vai trò × route")
    void routeMatrixIsEnforced(String who, String method, String path, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path);
        if (!"GET".equals(method) && !"DELETE".equals(method)) {
            request.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        switch (who) {
            case PATIENT -> request.header("Authorization", patientToken());
            case DOCTOR -> request.header("Authorization", doctorToken());
            case ADMIN -> request.header("Authorization", adminToken());
            default -> {
            }
        }
        mvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("401 và 403 trả đúng định dạng ApiError")
    void securityErrorsUseApiErrorFormat() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/admin/invoices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/admin/invoices"));

        mvc.perform(request(HttpMethod.GET, "/api/v1/admin/invoices").header("Authorization", patientToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/invoices"));
    }

    @Test
    @DisplayName("Không tìm thấy đường dẫn trả 404 ApiError, không phải 500")
    void unknownPathReturnsApiError404() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/khong-ton-tai").header("Authorization", patientToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/khong-ton-tai"));
    }
}
