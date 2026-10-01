package com.phenikaa.cse702051.medbook.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.phenikaa.cse702051.medbook.exception.ApiError;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private void assertError(ResponseEntity<ApiError> response, int status, ErrorCode code, String path) {
        assertEquals(status, response.getStatusCode().value());
        ApiError body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.timestamp());
        assertEquals(status, body.status());
        assertEquals(code.name(), body.code());
        assertEquals(path, body.path());
        assertNotNull(body.message());
    }

    @Test
    @DisplayName("Upload vượt giới hạn → 413 PAYLOAD_TOO_LARGE")
    void maxUploadSizeExceeded() {
        var response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(10L * 1024 * 1024), request("/api/v1/encounters/1/attachments"));

        assertError(response, 413, ErrorCode.PAYLOAD_TOO_LARGE, "/api/v1/encounters/1/attachments");
    }

    @Test
    @DisplayName("Không có handler/tài nguyên → 404, không phải 500")
    void noResource() {
        var response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/khong-ton-tai", "khong-ton-tai"),
                request("/api/v1/khong-ton-tai"));

        assertError(response, 404, ErrorCode.RESOURCE_NOT_FOUND, "/api/v1/khong-ton-tai");
    }

    @Test
    @DisplayName("AccessDeniedException (từ @PreAuthorize) → 403")
    void accessDenied() {
        var response = handler.handleAccessDenied(new AccessDeniedException("no"), request("/api/v1/x"));

        assertError(response, 403, ErrorCode.FORBIDDEN, "/api/v1/x");
    }

    @Test
    @DisplayName("AuthenticationException → 401")
    void authentication() {
        var response = handler.handleAuthentication(new BadCredentialsException("bad"), request("/api/v1/x"));

        assertError(response, 401, ErrorCode.UNAUTHORIZED, "/api/v1/x");
    }

    @Test
    @DisplayName("IllegalArgumentException vẫn 400 (tương thích) nhưng có ApiError đầy đủ")
    void illegalArgumentStaysBadRequest() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("sai"), request("/api/v1/x"));

        assertError(response, 400, ErrorCode.BAD_REQUEST, "/api/v1/x");
        assertEquals("sai", response.getBody().message());
    }

    @Test
    @DisplayName("ApiException con giữ đúng mã HTTP và mã lỗi")
    void apiExceptionSubclasses() {
        assertError(handler.handleApiException(new ForbiddenException("cấm"), request("/a")),
                403, ErrorCode.FORBIDDEN, "/a");
        assertError(handler.handleApiException(new ResourceNotFoundException("không có"), request("/a")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "/a");
        assertError(handler.handleApiException(new ConflictException("trùng"), request("/a")),
                409, ErrorCode.CONFLICT, "/a");
        assertError(handler.handleApiException(new UnauthorizedException("chưa"), request("/a")),
                401, ErrorCode.UNAUTHORIZED, "/a");
    }

    @Test
    @DisplayName("ResponseStatusException 413/422 được ánh xạ đúng mã lỗi")
    void responseStatusMapping() {
        assertError(handler.handleResponseStatusException(
                new ResponseStatusException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE, "lớn"),
                request("/a")), 413, ErrorCode.PAYLOAD_TOO_LARGE, "/a");
        assertError(handler.handleResponseStatusException(
                new ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "sai"),
                request("/a")), 422, ErrorCode.UNPROCESSABLE_ENTITY, "/a");
    }

    @Test
    @DisplayName("Lỗi bất ngờ → 500 ApiError, không lộ nội dung ngoại lệ")
    void unexpectedError() {
        var response = handler.handleUnexpected(new RuntimeException("bí mật nội bộ"), request("/a"));

        assertError(response, 500, ErrorCode.INTERNAL_ERROR, "/a");
        assertEquals(ErrorCode.INTERNAL_ERROR.getDefaultMessage(), response.getBody().message());
    }
}
