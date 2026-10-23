package com.phenikaa.cse702051.medbook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Mô tả chung của tài liệu OpenAPI. Tài liệu mặc định TẮT; bật bằng {@code medbook.openapi.enabled=true} (biến môi
 * trường {@code MEDBOOK_OPENAPI_ENABLED}) rồi mở {@code /swagger-ui.html} hoặc {@code /v3/api-docs}. Bản xuất tĩnh
 * nằm ở {@code docs/openapi.json}, do {@code OpenApiExportTest} sinh lại mỗi lần chạy test.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI medbookOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MedBook API")
                        .version("v1")
                        .description("API quản lý bệnh án và đặt lịch khám bệnh (CSE702051, Nhóm 03). Mọi đường dẫn "
                                + "nằm dưới /api/v1. Trừ đăng nhập, đăng ký và các danh mục công khai, mọi yêu cầu "
                                + "phải kèm header Authorization: Bearer <access token>. Lỗi luôn theo dạng ApiError "
                                + "(timestamp, status, error, code, message, path, details)."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token nhận từ POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
