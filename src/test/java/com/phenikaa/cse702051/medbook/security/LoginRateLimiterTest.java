package com.phenikaa.cse702051.medbook.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cửa sổ trượt của {@link LoginRateLimiter}, dùng đồng hồ giả để không phải chờ thật. */
class LoginRateLimiterTest {

    /** Đồng hồ chỉnh tay. */
    private static final class ManualClock extends Clock {
        private long millis = 1_000_000L;

        void advanceSeconds(long seconds) {
            millis += seconds * 1000L;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    @Test
    @DisplayName("Chạm ngưỡng thì bị chặn; hết cửa sổ thì được thử lại; báo chạm ngưỡng đúng một lần")
    void slidingWindow() {
        ManualClock clock = new ManualClock();
        LoginRateLimiter limiter = new LoginRateLimiter(3, 60, false, clock);

        assertEquals(0, limiter.retryAfterSeconds("a"));
        assertFalse(limiter.recordFailure("a"));
        clock.advanceSeconds(10);
        assertFalse(limiter.recordFailure("a"));
        assertEquals(0, limiter.retryAfterSeconds("a"));
        clock.advanceSeconds(10);
        assertTrue(limiter.recordFailure("a"), "lần sai thứ ba chạm ngưỡng");

        assertEquals(40, limiter.retryAfterSeconds("a"), "chờ tới khi lần sai đầu tiên ra khỏi cửa sổ 60 giây");
        assertEquals(0, limiter.retryAfterSeconds("b"), "IP khác không bị ảnh hưởng");

        clock.advanceSeconds(39);
        assertEquals(1, limiter.retryAfterSeconds("a"));
        clock.advanceSeconds(1);
        assertEquals(0, limiter.retryAfterSeconds("a"));

        // còn hai lần sai trong cửa sổ: sai thêm một lần là chạm ngưỡng lần nữa
        assertTrue(limiter.recordFailure("a"));
        assertTrue(limiter.retryAfterSeconds("a") > 0);
    }

    @Test
    @DisplayName("Không có IP (ngoài yêu cầu HTTP) hoặc ngưỡng bằng 0 thì không giới hạn")
    void disabledCases() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 60, false, new ManualClock());
        assertFalse(limiter.recordFailure(null));
        assertEquals(0, limiter.retryAfterSeconds(null));

        LoginRateLimiter off = new LoginRateLimiter(0, 60, false, new ManualClock());
        for (int i = 0; i < 20; i++) {
            assertFalse(off.recordFailure("a"));
        }
        assertEquals(0, off.retryAfterSeconds("a"));
    }
}
