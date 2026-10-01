package com.phenikaa.cse702051.medbook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các tác vụ định kỳ {@code @Scheduled} (dọn token đã thu hồi/hết hạn, nhắc lịch khám...).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
