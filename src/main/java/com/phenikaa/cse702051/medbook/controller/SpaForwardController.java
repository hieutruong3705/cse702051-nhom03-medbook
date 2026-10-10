package com.phenikaa.cse702051.medbook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Giao diện là ứng dụng một trang dùng địa chỉ thường ({@code /doctors}, {@code /patient/dashboard}). Khi người dùng
 * gõ thẳng hoặc tải lại một địa chỉ như vậy, máy chủ trả về {@code index.html} để bộ định tuyến phía trình duyệt hiện
 * đúng trang; trang cần đăng nhập do bộ định tuyến đó chuyển về trang đăng nhập.
 *
 * <p>Chỉ nhận đường dẫn mà đoạn cuối không có dấu chấm, nên yêu cầu tới một tệp ({@code /assets/khong-co.js}) vẫn nhận
 * 404 thật. Các đường của máy chủ ({@code /api}, tài liệu OpenAPI, {@code /assets}) không đi qua đây: API không bao giờ
 * trả về HTML.
 */
@Controller
public class SpaForwardController {

    /** Đoạn đầu: không phải khu của máy chủ. */
    private static final String FIRST = "(?!(?:api|v3|swagger-ui|assets)$)[^.]*";
    /** Đoạn cuối: không có phần mở rộng tệp. */
    private static final String LAST = "[^.]*";

    private static final String ONE = "/{a:" + FIRST + "}";
    private static final String TWO = ONE + "/{b:" + LAST + "}";
    private static final String THREE = ONE + "/{b}/{c:" + LAST + "}";
    private static final String FOUR = ONE + "/{b}/{c}/{d:" + LAST + "}";
    private static final String FIVE = ONE + "/{b}/{c}/{d}/{e:" + LAST + "}";

    /** Tới năm đoạn (trang sâu nhất hiện có ba đoạn), có hoặc không có dấu / ở cuối. */
    @GetMapping({ ONE, TWO, THREE, FOUR, FIVE, ONE + "/", TWO + "/", THREE + "/", FOUR + "/", FIVE + "/" })
    public String forwardToApp() {
        return "forward:/index.html";
    }
}
