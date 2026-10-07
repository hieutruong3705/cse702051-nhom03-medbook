package com.phenikaa.cse702051.medbook.auditquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.service.AuditEvent;
import com.phenikaa.cse702051.medbook.service.AuditLogService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * AC-01.8: Admin tra cứu nhật ký audit theo từng bộ lọc và theo tổ hợp, có phân trang, mới nhất trước. Mỗi test
 * ghi sự kiện với {@code entityType} riêng để không lẫn với audit do test khác sinh ra.
 */
class AuditLogQueryTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "/api/v1/admin/audit-logs";

    @Autowired
    private AuditLogService auditLogService;

    private static String uniqueType() {
        return "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long record(String action, String entityType, Long entityId, Long actorUserId) {
        return auditLogService.record(AuditEvent.of(action, entityType, entityId).byActor(actorUserId)).getId();
    }

    private JsonNode search(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request.header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("Lọc theo đối tượng: chỉ trả bản ghi của entityType và entityId đó, mới nhất trước, đủ trường")
    void filtersByEntityNewestFirst() throws Exception {
        String type = uniqueType();
        long first = record("TEST_VIEW", type, 10L, ADMIN_USER_ID);
        long second = record("TEST_EDIT", type, 10L, DOCTOR1_USER_ID);
        long otherEntity = record("TEST_VIEW", type, 11L, ADMIN_USER_ID);

        JsonNode page = search(get(URL).param("entityType", type).param("entityId", "10"));
        assertEquals(List.of(second, first), ids(page));
        JsonNode newest = page.get("content").get(0);
        assertEquals("TEST_EDIT", newest.get("actionCode").asText());
        assertEquals(DOCTOR1_USER_ID, newest.get("actorUserId").asLong());
        assertEquals(type, newest.get("entityType").asText());
        assertEquals(10L, newest.get("entityId").asLong());
        assertTrue(newest.hasNonNull("createdAt"));

        // chỉ lọc theo loại đối tượng: gồm cả entityId khác
        assertEquals(List.of(otherEntity, second, first), ids(search(get(URL).param("entityType", type))));
    }

    @Test
    @DisplayName("Lọc theo mã hành động và người thực hiện, riêng lẻ và kết hợp; mã hành động không phân biệt hoa thường")
    void filtersByActionAndActor() throws Exception {
        String type = uniqueType();
        long adminView = record("TEST_VIEW", type, 1L, ADMIN_USER_ID);
        long adminEdit = record("TEST_EDIT", type, 1L, ADMIN_USER_ID);
        long doctorView = record("TEST_VIEW", type, 1L, DOCTOR1_USER_ID);

        assertEquals(List.of(doctorView, adminView),
                ids(search(get(URL).param("entityType", type).param("actionCode", "test_view"))));
        assertEquals(List.of(adminEdit, adminView),
                ids(search(get(URL).param("entityType", type).param("actorUserId", String.valueOf(ADMIN_USER_ID)))));
        assertEquals(List.of(adminView), ids(search(get(URL).param("entityType", type)
                .param("actionCode", "TEST_VIEW").param("actorUserId", String.valueOf(ADMIN_USER_ID)))));
        assertEquals(List.of(), ids(search(get(URL).param("entityType", type).param("actionCode", "KHONG_CO"))));
        // tham số rỗng (giao diện gửi chuỗi rỗng khi không lọc) được bỏ qua
        assertEquals(3, search(get(URL).param("entityType", type).param("actionCode", "").param("actorUserId", ""))
                .get("totalElements").asInt());
    }

    @Test
    @DisplayName("Lọc theo khoảng ngày: tính trọn ngày bắt đầu và kết thúc; from sau to → 400 ở trường to")
    void filtersByDateRange() throws Exception {
        String type = uniqueType();
        long today = record("TEST_VIEW", type, 1L, ADMIN_USER_ID);
        String now = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        assertEquals(List.of(today), ids(search(get(URL).param("entityType", type).param("from", now).param("to", now))));
        assertEquals(List.of(today), ids(search(get(URL).param("entityType", type).param("from", yesterday))));
        assertEquals(List.of(), ids(search(get(URL).param("entityType", type).param("to", yesterday))));
        assertEquals(List.of(), ids(search(get(URL).param("entityType", type).param("from", tomorrow))));

        mvc.perform(get(URL).param("from", tomorrow).param("to", yesterday).header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.to").exists());
        mvc.perform(get(URL).param("from", "hom-nay").header("Authorization", adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phân trang: chia trang đúng thứ tự mới nhất trước; size lớn hơn 100 bị cắt còn 100")
    void paginatesAndCapsSize() throws Exception {
        String type = uniqueType();
        List<Long> recorded = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            recorded.add(record("TEST_VIEW", type, (long) i, ADMIN_USER_ID));
        }
        List<Long> newestFirst = recorded.reversed();

        JsonNode firstPage = search(get(URL).param("entityType", type).param("size", "2"));
        assertEquals(5, firstPage.get("totalElements").asInt());
        assertEquals(3, firstPage.get("totalPages").asInt());
        assertEquals(newestFirst.subList(0, 2), ids(firstPage));
        assertEquals(newestFirst.subList(2, 4), ids(search(get(URL).param("entityType", type).param("size", "2")
                .param("page", "1"))));
        assertEquals(newestFirst.subList(4, 5), ids(search(get(URL).param("entityType", type).param("size", "2")
                .param("page", "2"))));

        JsonNode capped = search(get(URL).param("entityType", type).param("size", "1000"));
        assertEquals(100, capped.get("size").asInt());
        assertEquals(20, search(get(URL).param("entityType", type)).get("size").asInt());
    }

    @Test
    @DisplayName("Chỉ Admin tra cứu được: khách 401, bệnh nhân và bác sĩ 403; danh sách mã hành động không trùng, đã sắp xếp")
    void onlyAdminCanQuery() throws Exception {
        String type = uniqueType();
        record("TEST_ZZ_UNIQUE_ACTION", type, 1L, ADMIN_USER_ID);
        record("TEST_ZZ_UNIQUE_ACTION", type, 2L, ADMIN_USER_ID);

        for (String url : new String[] { URL, URL + "/action-codes" }) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header("Authorization", patientToken())).andExpect(status().isForbidden());
            mvc.perform(get(url).header("Authorization", doctorToken())).andExpect(status().isForbidden());
        }

        MvcResult result = mvc.perform(get(URL + "/action-codes").header("Authorization", adminToken()))
                .andExpect(status().isOk()).andReturn();
        List<String> codes = new ArrayList<>();
        JSON.readTree(result.getResponse().getContentAsString()).forEach(node -> codes.add(node.asText()));
        assertEquals(1, codes.stream().filter("TEST_ZZ_UNIQUE_ACTION"::equals).count());
        assertEquals(codes.stream().sorted().toList(), codes);

        // đường dẫn cũ không phân trang đã bị gỡ
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", adminToken())).andExpect(status().isNotFound());
    }
}
