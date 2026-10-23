package com.phenikaa.cse702051.medbook.config;

import java.util.Arrays;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Nạp tài khoản và dữ liệu demo vào CSDL MySQL khi bật {@code medbook.seed.demo=true} (biến môi trường
 * {@code MEDBOOK_SEED_DEMO}).
 *
 * <ul>
 * <li>Chỉ nạp một lần, khi bảng {@code users} còn trống: khởi động lại không nạp chồng, và không bao giờ ghi đè dữ
 * liệu thật.</li>
 * <li>Không chạy ở hồ sơ {@code prod} dù cờ được bật: dữ liệu demo có mật khẩu công khai trong README.</li>
 * <li>Chạy sau khi Flyway đã dựng xong lược đồ (đây là {@link ApplicationRunner}).</li>
 * </ul>
 *
 * Chế độ phát triển bằng H2 không dùng lớp này mà nạp {@code data-h2.sql} qua {@code spring.sql.init}.
 */
@Component
@ConditionalOnProperty(name = "medbook.seed.demo", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DataSource dataSource;
    private final Resource script;
    private final boolean production;

    @Autowired
    public DemoSeedRunner(
            DataSource dataSource,
            @Value("${medbook.seed.script:classpath:db/seed/demo-seed.sql}") Resource script,
            Environment environment) {
        this(dataSource, script, Arrays.asList(environment.getActiveProfiles()).contains("prod"));
    }

    public DemoSeedRunner(DataSource dataSource, Resource script, boolean production) {
        this.dataSource = dataSource;
        this.script = script;
        this.production = production;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    /** @return {@code true} nếu đã nạp dữ liệu demo trong lần gọi này */
    public boolean seedIfEmpty() {
        if (production) {
            log.warn("MEDBOOK_SEED_DEMO bị bỏ qua ở hồ sơ prod: không nạp dữ liệu demo vào môi trường thật.");
            return false;
        }
        Long users = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM users", Long.class);
        if (users != null && users > 0) {
            log.info("CSDL đã có {} tài khoản, không nạp dữ liệu demo.", users);
            return false;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        log.info("Đã nạp dữ liệu demo từ {}.", script.getDescription());
        return true;
    }
}
