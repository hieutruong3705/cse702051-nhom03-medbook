package com.phenikaa.cse702051.medbook;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MedbookApplication {

	/** Biến môi trường chọn múi giờ của phòng khám. */
	static final String TIME_ZONE_VARIABLE = "MEDBOOK_TIME_ZONE";

	/** Múi giờ mặc định: phòng khám ở Việt Nam. */
	static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	public static void main(String[] args) {
		applyTimeZone(System.getenv(TIME_ZONE_VARIABLE));
		SpringApplication.run(MedbookApplication.class, args);
	}

	/**
	 * Đặt múi giờ của tiến trình theo giờ phòng khám, trước khi bất kỳ thành phần nào đọc đồng hồ.
	 *
	 * <p>"Hôm nay", "khung giờ đã qua" và hạn hủy trước giờ khám đều so với giờ của tiến trình. Máy chủ và container
	 * thường chạy giờ UTC; để nguyên thì mọi phép so đó chậm 7 giờ so với giờ Việt Nam (khung 08:00 vẫn đặt được tới
	 * 15:00). Các hồ sơ MySQL đặt {@code hibernate.jdbc.time_zone} theo cùng biến này, nên ngày giờ trong CSDL được
	 * lưu và đọc đúng như giờ phòng khám.
	 */
	static ZoneId applyTimeZone(String configured) {
		ZoneId zone = resolveTimeZone(configured);
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		return zone;
	}

	/** Múi giờ theo cấu hình; để trống thì dùng {@link #DEFAULT_TIME_ZONE}. Tên sai thì dừng ngay, không đoán. */
	static ZoneId resolveTimeZone(String configured) {
		if (configured == null || configured.isBlank()) {
			return DEFAULT_TIME_ZONE;
		}
		try {
			return ZoneId.of(configured.trim());
		} catch (DateTimeException e) {
			throw new IllegalArgumentException(TIME_ZONE_VARIABLE + " không phải tên múi giờ hợp lệ: \"" + configured.trim()
					+ "\". Ví dụ đúng: Asia/Ho_Chi_Minh, UTC.", e);
		}
	}

}
