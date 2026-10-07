package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.config.DemoActivitySeeder;
import com.phenikaa.cse702051.medbook.config.DemoActivitySeeder.Summary;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * Dữ liệu mẫu cho báo cáo: ba báo cáo của quản trị viên phải trả đúng số liệu mà bộ nạp đã sinh, và bộ nạp không
 * chạy lại khi CSDL đã có hoạt động. Test chạy trong một giao dịch được hoàn tác, ở một tháng trong quá khứ xa, nên
 * không để lại gì cho các test khác.
 */
@Transactional
class DemoActivitySeederTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(1995, 3, 1);
    private static final String RANGE = "?from=1995-02-01&to=1995-02-28";

    @Autowired
    private DemoActivitySeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode report(String path) throws Exception {
        return JSON.readTree(mvc.perform(get("/api/v1/admin/reports/" + path).header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static long added(JsonNode after, JsonNode before, String field) {
        return after.get(field).asLong() - before.get(field).asLong();
    }

    private static BigDecimal money(JsonNode node) {
        return new BigDecimal(node.asText()).setScale(2);
    }

    @Test
    @DisplayName("Dữ liệu mẫu: ba báo cáo khớp số liệu đã nạp, đủ mọi trạng thái để biểu đồ có đủ các chuỗi")
    void reportsMatchSeededActivity() throws Exception {
        // Số liệu có sẵn trong kỳ (test khác có thể đã để lại) được trừ đi: chỉ so phần do bộ nạp thêm vào
        JsonNode appointmentsBefore = report("appointments" + RANGE);
        JsonNode revenueBefore = report("revenue" + RANGE);

        Summary summary = seeder.generate(TODAY).orElseThrow();

        assertTrue(summary.completed() > 0 && summary.cancelled() > 0 && summary.booked() > 0, summary.toString());
        assertTrue(summary.paidInvoices() > 0 && summary.unpaidInvoices() > 0 && summary.voidInvoices() > 0,
                summary.toString());
        assertEquals(summary.completed(),
                summary.paidInvoices() + summary.unpaidInvoices() + summary.voidInvoices(),
                "mỗi lần khám hoàn thành có đúng một hóa đơn");

        JsonNode appointments = report("appointments" + RANGE + "&groupBy=DAY");
        assertEquals(summary.appointments(), added(appointments, appointmentsBefore, "total"));
        assertEquals(summary.completed(), added(appointments, appointmentsBefore, "completed"));
        assertEquals(summary.cancelled(), added(appointments, appointmentsBefore, "cancelled"));
        assertEquals(summary.booked(), added(appointments, appointmentsBefore, "booked"));
        assertEquals(DemoActivitySeeder.DAYS, appointments.get("groups").size(), "ngày nào trong kỳ cũng có lịch");

        JsonNode revenue = report("revenue" + RANGE + "&groupBy=DAY");
        assertEquals(summary.collectedAmount(),
                money(revenue.get("collectedAmount")).subtract(money(revenueBefore.get("collectedAmount"))));
        assertEquals(summary.unpaidAmount(),
                money(revenue.get("unpaidAmount")).subtract(money(revenueBefore.get("unpaidAmount"))));
        assertEquals(summary.paidInvoices() + summary.unpaidInvoices(), added(revenue, revenueBefore, "invoiceCount"));
        assertEquals(summary.voidInvoices(), added(revenue, revenueBefore, "voidCount"));

        JsonNode services = report("services" + RANGE);
        assertTrue(services.get("services").size() >= 2, "biểu đồ dịch vụ cần từ hai dịch vụ trở lên");
        // Thành tiền theo dịch vụ tính trước giảm giá của cả hóa đơn nên không nhỏ hơn giá trị lập hóa đơn
        assertTrue(money(services.get("totalAmount")).compareTo(money(revenue.get("invoicedAmount"))) >= 0);

        assertEquals(0, jdbc.queryForObject(
                "select count(*) from appointments a join appointment_slots s on s.id = a.slot_id "
                        + "where s.slot_date between '1995-02-01' and '1995-02-28' and a.created_at > s.slot_date",
                Long.class), "thời điểm đặt phải trước ngày khám");
    }

    @Test
    @DisplayName("Dữ liệu mẫu: không nạp chồng khi CSDL đã có lịch hẹn")
    void doesNotSeedTwice() {
        seeder.generate(TODAY).orElseThrow();

        assertTrue(seeder.seedIfEmpty(TODAY).isEmpty());
    }
}
