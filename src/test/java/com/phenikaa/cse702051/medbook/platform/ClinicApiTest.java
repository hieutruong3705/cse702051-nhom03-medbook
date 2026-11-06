package com.phenikaa.cse702051.medbook.platform;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.phenikaa.cse702051.medbook.support.AbstractApiTest;

/** YCCN-37: trang giới thiệu phòng khám xem được không cần đăng nhập; con số lấy từ CSDL; có trong bản đồ trang. */
class ClinicApiTest extends AbstractApiTest {

    @Test
    @DisplayName("GET /clinic công khai: thông tin cấu hình và số bác sĩ, chuyên khoa, dịch vụ đang hoạt động")
    void clinicInfoIsPublic() throws Exception {
        mvc.perform(get("/api/v1/clinic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Phòng khám đa khoa MedBook"))
                .andExpect(jsonPath("$.workingHours", hasSize(3)))
                .andExpect(jsonPath("$.doctorCount", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.specialtyCount", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.serviceCount", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.reviewCount", greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Bản đồ trang có /about, /reviews và trang chi tiết của bác sĩ, chuyên khoa mẫu")
    void sitemapListsNewPublicPages() throws Exception {
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.allOf(
                        Matchers.containsString("<loc>http://localhost/about</loc>"),
                        Matchers.containsString("<loc>http://localhost/reviews</loc>"),
                        Matchers.containsString("<loc>http://localhost/doctors/1</loc>"),
                        Matchers.containsString("<loc>http://localhost/specialties/1</loc>"))));
    }
}
