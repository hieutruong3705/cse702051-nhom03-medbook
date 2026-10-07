package com.phenikaa.cse702051.medbook.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Giới hạn tần suất đăng nhập sai theo địa chỉ IP (chống dò mật khẩu trên nhiều tài khoản từ một máy). Trong một
 * cửa sổ {@code window-seconds} (mặc định 60 giây), một IP chỉ được đăng nhập sai {@code max-failures} lần (mặc
 * định 5, nên lần thứ sáu liên tiếp nhận 429); vượt ngưỡng thì mọi lần
 * đăng nhập từ IP đó bị từ chối với mã 429 cho tới khi các lần sai cũ trôi khỏi cửa sổ. Đây là lớp bổ sung cho
 * việc khóa tạm từng tài khoản sau nhiều lần sai mật khẩu (xem {@code AuthService}).
 *
 * <p>Bộ đếm nằm trong bộ nhớ của tiến trình: đủ cho một máy chủ; khi chạy nhiều bản sao cần chuyển sang kho dùng
 * chung. Chỉ tin {@code X-Forwarded-For} khi {@code medbook.trust-proxy=true}, nếu không client có thể giả IP để
 * né giới hạn.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final int maxFailures;
    private final long windowMillis;
    private final boolean trustProxy;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> failuresByClient = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimiter(
            @Value("${medbook.security.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${medbook.security.login-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${medbook.trust-proxy:false}") boolean trustProxy) {
        this(maxFailures, windowSeconds, trustProxy, Clock.systemUTC());
    }

    LoginRateLimiter(int maxFailures, long windowSeconds, boolean trustProxy, Clock clock) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowSeconds * 1000L;
        this.trustProxy = trustProxy;
        this.clock = clock;
    }

    /** Địa chỉ IP của yêu cầu đang xử lý; {@code null} khi không chạy trong một yêu cầu HTTP. */
    public String currentClient() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (trustProxy) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * @return số giây phải chờ trước khi được đăng nhập lại; 0 nếu chưa vượt ngưỡng
     */
    public long retryAfterSeconds(String client) {
        if (client == null || maxFailures <= 0) {
            return 0;
        }
        Deque<Long> failures = failuresByClient.get(client);
        if (failures == null) {
            return 0;
        }
        long now = clock.millis();
        synchronized (failures) {
            dropExpired(failures, now);
            if (failures.size() < maxFailures) {
                return 0;
            }
            long oldestCounted = failures.peekFirst();
            return Math.max(1, (oldestCounted + windowMillis - now + 999) / 1000);
        }
    }

    /**
     * Ghi nhận một lần đăng nhập sai.
     *
     * @return {@code true} đúng một lần, khi lần sai này làm IP chạm ngưỡng (để ghi nhật ký một lần cho mỗi đợt)
     */
    public boolean recordFailure(String client) {
        if (client == null || maxFailures <= 0) {
            return false;
        }
        long now = clock.millis();
        if (failuresByClient.size() > MAX_TRACKED_CLIENTS) {
            failuresByClient.entrySet().removeIf(entry -> isIdle(entry.getValue(), now));
        }
        Deque<Long> failures = failuresByClient.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (failures) {
            dropExpired(failures, now);
            failures.addLast(now);
            return failures.size() == maxFailures;
        }
    }

    private boolean isIdle(Deque<Long> failures, long now) {
        synchronized (failures) {
            dropExpired(failures, now);
            return failures.isEmpty();
        }
    }

    private void dropExpired(Deque<Long> failures, long now) {
        while (!failures.isEmpty() && failures.peekFirst() + windowMillis <= now) {
            failures.pollFirst();
        }
    }
}
