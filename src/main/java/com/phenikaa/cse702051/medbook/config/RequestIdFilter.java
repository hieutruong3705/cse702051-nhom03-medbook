package com.phenikaa.cse702051.medbook.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gắn cho mỗi yêu cầu một mã định danh: ghi vào mọi dòng log của yêu cầu đó (MDC {@code requestId}), trả về ở tiêu
 * đề {@code X-Request-Id} và ở trường {@code requestId} của thân lỗi. Người dùng chỉ cần báo lại mã này là tra được
 * đúng dòng log, nên thân lỗi không phải chứa chi tiết nội bộ.
 *
 * <p>Chạy trước mọi bộ lọc khác (kể cả bảo mật) để lỗi 401, 403 cũng có mã. Mã do phía gọi gửi lên chỉ được dùng
 * lại khi đúng khuôn dạng an toàn; nếu không thì sinh mã mới để không ai chèn được nội dung tùy ý vào log.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    /** Mã của yêu cầu đang xử lý trên luồng hiện tại; {@code null} khi không ở trong một yêu cầu HTTP. */
    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
