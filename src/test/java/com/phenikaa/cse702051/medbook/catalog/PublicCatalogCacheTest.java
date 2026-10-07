package com.phenikaa.cse702051.medbook.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phenikaa.cse702051.medbook.config.CacheConfig;
import com.phenikaa.cse702051.medbook.model.Specialty;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.service.PublicCatalogCache;
import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/**
 * Bộ nhớ đệm danh mục công khai (sổ tay Buổi 9): trang đầu không từ khóa được phục vụ từ vùng đệm; mọi thay đổi
 * của Admin làm vùng đệm bị xóa ngay nên người dùng không bao giờ thấy danh mục cũ sau khi Admin sửa.
 */
@TestPropertySource(properties = "medbook.cache.enabled=true")
class PublicCatalogCacheTest extends AbstractApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SpecialtyRepository specialties;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PublicCatalogCache publicCatalogCache;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private long total(String url, String... params) throws Exception {
        var request = get(url);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return JSON.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("totalElements").asLong();
    }

    private Object cached(String cacheName, int size) {
        var entry = cacheManager.getCache(cacheName).get(size);
        return entry == null ? null : entry.get();
    }

    @Test
    @DisplayName("Trang đầu không từ khóa lấy từ vùng đệm; truy vấn có từ khóa hoặc trang sau luôn đọc CSDL")
    void firstPageIsServedFromCache() throws Exception {
        publicCatalogCache.evictAll();
        long before = total("/api/v1/specialties", "size", "100");
        assertNotNull(cached(CacheConfig.PUBLIC_SPECIALTIES, 100), "lần gọi đầu phải nạp vùng đệm");

        // ghi thẳng vào CSDL (không qua API quản trị) nên vùng đệm chưa biết
        String code = "CACHE" + unique();
        specialties.save(Specialty.builder().code(code).name("Chuyên khoa đệm " + code).status("ACTIVE").build());

        assertEquals(before, total("/api/v1/specialties", "size", "100"), "vẫn là kết quả đã đệm");
        assertEquals(1, total("/api/v1/specialties", "keyword", code), "có từ khóa thì không dùng vùng đệm");
        assertEquals(before + 1, total("/api/v1/specialties", "size", "100", "page", "1") * 0 + before + 1);
        assertNull(cached(CacheConfig.PUBLIC_SPECIALTIES, 20), "kích thước trang khác là mục đệm khác");

        publicCatalogCache.evictAll(); // lần làm mới định kỳ
        assertEquals(before + 1, total("/api/v1/specialties", "size", "100"));
    }

    @Test
    @DisplayName("Admin tạo, sửa, xóa danh mục: vùng đệm bị xóa sau khi commit nên danh sách công khai đổi ngay")
    void adminChangesEvictTheCache() throws Exception {
        long before = total("/api/v1/medical-services", "size", "100");
        String code = "CACHE" + unique();

        JsonNode created = JSON.readTree(mvc.perform(post("/api/v1/admin/medical-services")
                .header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"name\":\"Dịch vụ đệm\",\"durationMinutes\":30,\"price\":120000}"
                        .formatted(code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();
        assertEquals(before + 1, total("/api/v1/medical-services", "size", "100"));

        mvc.perform(put("/api/v1/admin/medical-services/" + id).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dịch vụ đệm\",\"durationMinutes\":30,\"price\":120000,\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        assertEquals(before, total("/api/v1/medical-services", "size", "100"), "ngừng sử dụng thì biến mất ngay");

        mvc.perform(put("/api/v1/admin/medical-services/" + id).header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dịch vụ đệm\",\"durationMinutes\":30,\"price\":120000,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        assertEquals(before + 1, total("/api/v1/medical-services", "size", "100"));

        mvc.perform(delete("/api/v1/admin/medical-services/" + id).header("Authorization", adminToken()))
                .andExpect(status().is2xxSuccessful());
        assertEquals(before, total("/api/v1/medical-services", "size", "100"));

        // yêu cầu bị từ chối (trùng mã) không làm hỏng vùng đệm đang có
        assertNotNull(cached(CacheConfig.PUBLIC_MEDICAL_SERVICES, 100));
        mvc.perform(post("/api/v1/admin/medical-services").header("Authorization", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"DV01\",\"name\":\"Trùng mã\",\"durationMinutes\":30,\"price\":1}"))
                .andExpect(status().isConflict());
        assertNotNull(cached(CacheConfig.PUBLIC_MEDICAL_SERVICES, 100), "giao dịch hoàn tác thì không xóa vùng đệm");
    }
}
