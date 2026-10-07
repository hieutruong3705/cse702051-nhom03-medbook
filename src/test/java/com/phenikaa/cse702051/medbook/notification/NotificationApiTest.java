package com.phenikaa.cse702051.medbook.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.model.Notification;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.service.NotificationService;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedDoctor;
import com.phenikaa.cse702051.medbook.support.ApiTestData.IsolatedPatient;

/**
 * API đọc và đánh dấu thông báo: mỗi người chỉ thấy và chỉ đánh dấu được thông báo của chính mình. Thông báo được
 * dựng thẳng vào CSDL cho người dùng riêng của từng test.
 */
class NotificationApiTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private UserRepository users;

    private long seed(long userId, String title, LocalDateTime createdAt, boolean read) {
        return notifications.save(Notification.builder()
                .user(users.getReferenceById(userId))
                .type(NotificationService.APPOINTMENT_BOOKED)
                .title(title)
                .message("Nội dung " + title)
                .appointmentId(null)
                .createdAt(createdAt)
                .readAt(read ? createdAt.plusMinutes(1) : null)
                .build()).getId();
    }

    private JsonNode getJson(String token, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("AC-05.7 Danh sách của tôi: mới nhất trước, phân trang, lọc chưa đọc, đếm chưa đọc")
    void listsOwnNotificationsNewestFirst() throws Exception {
        IsolatedPatient patient = data.isolatedPatient("ntf_api_list");
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        long oldest = seed(patient.userId(), "Cũ nhất", base, true);
        long middle = seed(patient.userId(), "Ở giữa", base.plusHours(1), false);
        long newest = seed(patient.userId(), "Mới nhất", base.plusHours(2), false);

        JsonNode all = getJson(patient.token(), "/api/v1/notifications/me");
        assertEquals(List.of(newest, middle, oldest), ids(all));
        assertEquals(3, all.get("totalElements").asInt());
        JsonNode first = all.get("content").get(0);
        assertEquals("Mới nhất", first.get("title").asText());
        assertEquals("Nội dung Mới nhất", first.get("message").asText());
        assertEquals(NotificationService.APPOINTMENT_BOOKED, first.get("type").asText());
        assertEquals(true, first.get("readAt").isNull());
        assertEquals(false, all.get("content").get(2).get("readAt").isNull());
        assertEquals(false, first.has("user"), "không lộ thông tin tài khoản trong thông báo");

        assertEquals(List.of(newest, middle), ids(getJson(patient.token(), "/api/v1/notifications/me?unreadOnly=true")));
        JsonNode paged = getJson(patient.token(), "/api/v1/notifications/me?size=2&page=1");
        assertEquals(List.of(oldest), ids(paged));
        assertEquals(2, paged.get("totalPages").asInt());
        assertEquals(100, getJson(patient.token(), "/api/v1/notifications/me?size=999").get("size").asInt());

        assertEquals(2, getJson(patient.token(), "/api/v1/notifications/me/unread-count").get("count").asInt());
    }

    @Test
    @DisplayName("AC-05.7 Đánh dấu đã đọc một thông báo: 204, giảm số chưa đọc; đánh dấu lại không đổi thời điểm đọc")
    void marksOneAsRead() throws Exception {
        IsolatedPatient patient = data.isolatedPatient("ntf_api_read");
        long first = seed(patient.userId(), "Một", LocalDateTime.now().minusHours(2), false);
        long second = seed(patient.userId(), "Hai", LocalDateTime.now().minusHours(1), false);

        mvc.perform(patch("/api/v1/notifications/%d/read".formatted(first)).header("Authorization", patient.token()))
                .andExpect(status().isNoContent());
        LocalDateTime readAt = notifications.findById(first).orElseThrow().getReadAt();
        assertNotNull(readAt);
        assertNull(notifications.findById(second).orElseThrow().getReadAt());
        assertEquals(1, getJson(patient.token(), "/api/v1/notifications/me/unread-count").get("count").asInt());

        mvc.perform(patch("/api/v1/notifications/%d/read".formatted(first)).header("Authorization", patient.token()))
                .andExpect(status().isNoContent());
        assertEquals(readAt, notifications.findById(first).orElseThrow().getReadAt());
        mvc.perform(patch("/api/v1/notifications/999999/read").header("Authorization", patient.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AC-05.7 Đánh dấu tất cả đã đọc: chỉ thông báo của chính mình")
    void marksAllOwnAsRead() throws Exception {
        IsolatedPatient me = data.isolatedPatient("ntf_api_all_me");
        IsolatedPatient other = data.isolatedPatient("ntf_api_all_other");
        seed(me.userId(), "Của tôi 1", LocalDateTime.now().minusHours(2), false);
        seed(me.userId(), "Của tôi 2", LocalDateTime.now().minusHours(1), false);
        long theirs = seed(other.userId(), "Của người khác", LocalDateTime.now().minusHours(1), false);

        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", me.token()))
                .andExpect(status().isNoContent());

        assertEquals(0, getJson(me.token(), "/api/v1/notifications/me/unread-count").get("count").asInt());
        assertEquals(0, getJson(me.token(), "/api/v1/notifications/me?unreadOnly=true").get("totalElements").asInt());
        assertNull(notifications.findById(theirs).orElseThrow().getReadAt());
        assertEquals(1, getJson(other.token(), "/api/v1/notifications/me/unread-count").get("count").asInt());
    }

    @Test
    @DisplayName("AC-05.6 Thông báo của người khác: không có trong danh sách; đánh dấu đã đọc trả 404 và không đổi gì")
    void cannotSeeOrMarkOthersNotifications() throws Exception {
        IsolatedPatient owner = data.isolatedPatient("ntf_api_owner");
        IsolatedPatient intruder = data.isolatedPatient("ntf_api_intruder");
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_api_doctor");
        long ownersNotification = seed(owner.userId(), "Riêng tư", LocalDateTime.now().minusMinutes(5), false);

        for (String token : new String[] { intruder.token(), doctor.token() }) {
            assertEquals(false, ids(getJson(token, "/api/v1/notifications/me")).contains(ownersNotification));
            mvc.perform(patch("/api/v1/notifications/%d/read".formatted(ownersNotification))
                    .header("Authorization", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        assertNull(notifications.findById(ownersNotification).orElseThrow().getReadAt());
        assertEquals(List.of(ownersNotification), ids(getJson(owner.token(), "/api/v1/notifications/me")));
    }

    @Test
    @DisplayName("AC-05.7 Quyền: khách 401, Admin 403 ở mọi đường dẫn thông báo; bác sĩ dùng được")
    void onlyPatientsAndDoctorsUseNotifications() throws Exception {
        IsolatedDoctor doctor = data.isolatedDoctor("ntf_api_role_doc");
        seed(doctor.userId(), "Cho bác sĩ", LocalDateTime.now().minusMinutes(1), false);

        mvc.perform(get("/api/v1/notifications/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/me/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/notifications/me").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications/me/unread-count").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/notifications/1/read").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());

        assertEquals(1, getJson(doctor.token(), "/api/v1/notifications/me").get("totalElements").asInt());
        assertEquals(1, getJson(doctor.token(), "/api/v1/notifications/me/unread-count").get("count").asInt());
    }
}
