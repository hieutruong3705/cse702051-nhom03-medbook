package com.phenikaa.cse702051.medbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Múi giờ của ứng dụng: mặc định là giờ Việt Nam để "hôm nay" và hạn hủy trước giờ khám đúng với giờ phòng khám, kể cả
 * khi máy chủ chạy giờ UTC; các hồ sơ MySQL lưu và đọc ngày giờ theo cùng múi giờ đó.
 */
class TimeZoneSetupTest {

    private TimeZone before;

    @BeforeEach
    void remember() {
        before = TimeZone.getDefault();
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(before);
    }

    @Test
    @DisplayName("Không cấu hình gì thì ứng dụng chạy theo giờ Việt Nam")
    void defaultsToVietnamTime() {
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), MedbookApplication.resolveTimeZone(null));
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), MedbookApplication.resolveTimeZone(""));
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), MedbookApplication.resolveTimeZone("   "));
    }

    @Test
    @DisplayName("MEDBOOK_TIME_ZONE được dùng khi có, bỏ khoảng trắng thừa")
    void configuredZoneIsUsed() {
        assertEquals(ZoneId.of("UTC"), MedbookApplication.resolveTimeZone("UTC"));
        assertEquals(ZoneId.of("Asia/Tokyo"), MedbookApplication.resolveTimeZone(" Asia/Tokyo "));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Asia/Ha_Noi_Khong_Co", "GMT+25", "viet nam" })
    @DisplayName("Tên múi giờ sai làm ứng dụng dừng ngay với thông báo nêu tên biến")
    void invalidZoneStopsTheApplication(String value) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MedbookApplication.resolveTimeZone(value));
        assertTrue(error.getMessage().contains("MEDBOOK_TIME_ZONE"), error.getMessage());
        assertTrue(error.getMessage().contains(value.trim()), error.getMessage());
    }

    @Test
    @DisplayName("Máy chủ chạy giờ UTC: sau khi áp múi giờ, đồng hồ của ứng dụng là giờ Việt Nam (lệch đúng 7 giờ)")
    void serverInUtcStillSeesClinicTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDateTime serverClock = LocalDateTime.now();

        ZoneId applied = MedbookApplication.applyTimeZone(null);

        assertEquals("Asia/Ho_Chi_Minh", TimeZone.getDefault().getID());
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), applied);
        long hoursAhead = ChronoUnit.HOURS.between(serverClock, LocalDateTime.now().plusMinutes(1));
        assertEquals(7, hoursAhead);
        assertEquals(ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), LocalDateTime.now().toLocalDate());
    }

    @ParameterizedTest
    @ValueSource(strings = { "application-docker.yml", "application-prod.yaml", "application-online.yaml",
            "application-v4mysql.yaml" })
    @DisplayName("Các hồ sơ MySQL lưu và đọc ngày giờ theo đúng múi giờ của ứng dụng, không cố định UTC")
    void mysqlProfilesFollowTheApplicationZone(String profile) throws Exception {
        String yaml = new ClassPathResource(profile).getContentAsString(StandardCharsets.UTF_8);
        assertTrue(yaml.contains("jdbc.time_zone: ${MEDBOOK_TIME_ZONE:Asia/Ho_Chi_Minh}"), profile);
    }
}
