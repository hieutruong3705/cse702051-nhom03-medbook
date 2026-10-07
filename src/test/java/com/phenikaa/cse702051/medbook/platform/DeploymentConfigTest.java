package com.phenikaa.cse702051.medbook.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import com.phenikaa.cse702051.medbook.config.DeploymentConfigGuard;

/**
 * AC-09.3: ở hồ sơ {@code docker} và {@code prod}, thiếu {@code JWT_SECRET} thì cấu hình không phân giải được và
 * thông báo nêu đúng tên biến; khóa quá ngắn hoặc là giá trị mẫu công khai cũng bị từ chối. Môi trường của test là
 * {@link MockEnvironment} nên không phụ thuộc biến môi trường của máy đang chạy.
 */
class DeploymentConfigTest {

    private static final String GOOD_SECRET = "kPq7Zr2Xv9Lm4Nw8Ty6Ub3Hc5Jd1Fg0SaEoIuWxYz";

    private static MockEnvironment load(String resource) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(resource,
                new ClassPathResource(resource));
        sources.forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "application-docker.yml", "application-prod.yaml" })
    @DisplayName("AC-09.3 Thiếu JWT_SECRET: không phân giải được jwt.secret và thông báo nêu tên biến")
    void missingJwtSecretIsReportedByName(String resource) throws IOException {
        MockEnvironment environment = load(resource);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("jwt.secret"));
        assertTrue(error.getMessage().contains("JWT_SECRET"), error.getMessage());

        environment.setProperty("JWT_SECRET", GOOD_SECRET);
        assertEquals(GOOD_SECRET, environment.getRequiredProperty("jwt.secret"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "application-docker.yml", "application-prod.yaml" })
    @DisplayName("Hồ sơ triển khai: Flyway bật, Hibernate chỉ validate, không nạp data-h2.sql, có baseline cho CSDL đã có")
    void deploymentProfilesUseFlywayAndValidate(String resource) throws IOException {
        MockEnvironment environment = load(resource);

        assertEquals("true", environment.getProperty("spring.flyway.enabled"));
        assertEquals("classpath:db/migration", environment.getProperty("spring.flyway.locations"));
        assertEquals("true", environment.getProperty("spring.flyway.baseline-on-migrate"));
        assertEquals("1", environment.getProperty("spring.flyway.baseline-version"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", environment.getProperty("spring.jpa.defer-datasource-initialization"),
                "Flyway phải chạy trước khi Hibernate validate");
        assertEquals("never", environment.getProperty("spring.sql.init.mode"));
        assertEquals("framework", environment.getProperty("server.forward-headers-strategy"));
    }

    @Test
    @DisplayName("Hồ sơ prod: không có giá trị mặc định cho CSDL, CORS, thư mục tệp; không bao giờ nạp dữ liệu demo")
    void prodHasNoDefaultsAndNeverSeeds() throws IOException {
        MockEnvironment environment = load("application-prod.yaml");

        for (String[] property : new String[][] {
                { "spring.datasource.url", "MEDBOOK_DB_URL" },
                { "spring.datasource.username", "MEDBOOK_DB_USER" },
                { "spring.datasource.password", "MEDBOOK_DB_PASSWORD" },
                { "medbook.upload-dir", "MEDBOOK_UPLOAD_DIR" },
                { "medbook.cors.allowed-origins", "MEDBOOK_CORS_ALLOWED_ORIGINS" } }) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> environment.getRequiredProperty(property[0]), property[0]);
            assertTrue(error.getMessage().contains(property[1]), error.getMessage());
        }
        environment.setProperty("MEDBOOK_SEED_DEMO", "true"); // dù có đặt cờ, prod vẫn không nạp
        assertEquals("false", environment.getProperty("medbook.seed.demo"));
        assertEquals("false", environment.getProperty("medbook.openapi.enabled"));
    }

    @Test
    @DisplayName("Hồ sơ docker: dữ liệu demo và OpenAPI chỉ bật khi đặt cờ; mật khẩu CSDL không có giá trị mặc định")
    void dockerSeedsOnlyWhenAsked() throws IOException {
        MockEnvironment environment = load("application-docker.yml");
        assertEquals("false", environment.getProperty("medbook.seed.demo"));
        assertThrows(IllegalArgumentException.class,
                () -> environment.getRequiredProperty("spring.datasource.password"));

        environment.setProperty("MEDBOOK_SEED_DEMO", "true");
        assertEquals("true", environment.getProperty("medbook.seed.demo"));
    }

    @Test
    @DisplayName("AC-09.3 Khóa JWT rỗng, ngắn hơn 32 byte hoặc là giá trị mẫu công khai: từ chối khởi động với lý do rõ ràng")
    void weakOrPublicJwtSecretsAreRejected() {
        assertTrue(assertThrows(IllegalStateException.class, () -> DeploymentConfigGuard.validate(" "))
                .getMessage().contains("JWT_SECRET"));
        assertTrue(assertThrows(IllegalStateException.class, () -> DeploymentConfigGuard.validate("ngan-qua"))
                .getMessage().contains("quá ngắn"));
        for (String sample : new String[] {
                "MedBookSecretKey2024VeryLongSecureKeyForHS256AlgorithmAtLeast256Bits!!",
                "your-super-secret-key-change-in-production-min-32-chars",
                "doi-thanh-chuoi-ngau-nhien-dai-it-nhat-32-ky-tu" }) {
            assertTrue(assertThrows(IllegalStateException.class, () -> DeploymentConfigGuard.validate(sample))
                    .getMessage().contains("giá trị mẫu"), sample);
        }
        assertDoesNotThrow(() -> DeploymentConfigGuard.validate(GOOD_SECRET));
    }
}
