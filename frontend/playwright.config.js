import { defineConfig } from '@playwright/test'

/**
 * Kiểm thử đầu-cuối trên ứng dụng THẬT đang chạy (backend + giao diện đã build), không dùng dữ liệu giả.
 *
 *   1. Chạy ứng dụng ở chế độ phát triển (H2, có sẵn tài khoản demo), ví dụ cổng 8089:
 *        $env:PORT = '8089'; .\mvnw.cmd spring-boot:run          (ở thư mục gốc)
 *   2. Trong thư mục frontend:
 *        npm run e2e
 *
 * Địa chỉ ứng dụng lấy từ biến E2E_BASE_URL (mặc định http://127.0.0.1:8089). Trình duyệt dùng Microsoft Edge có
 * sẵn trên Windows nên không phải tải trình duyệt riêng; đổi bằng E2E_BROWSER_CHANNEL (ví dụ "chrome").
 * Tệp test có đuôi .e2e.js để Vitest không chạy nhầm.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.js',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:8089',
    channel: process.env.E2E_BROWSER_CHANNEL || 'msedge',
    locale: 'vi-VN',
    viewport: { width: 1366, height: 800 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  }
})
