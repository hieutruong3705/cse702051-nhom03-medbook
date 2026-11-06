import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { PASSWORD, isoDay, login } from './support'

// M5 (UX sinh động): hiện dần khi cuộn, tôn trọng "giảm chuyển động", và màn hình đặt lịch thành công có tệp .ics.

test('Chuyển động bình thường: thẻ ngoài màn hình hiện dần khi cuộn tới', async ({ page }) => {
  await page.goto('/')
  const doctorCard = page.locator('a[href^="/doctors/"].reveal').first()
  await expect(doctorCard).toHaveCount(1)
  await expect(doctorCard).toHaveCSS('opacity', '0')
  await doctorCard.scrollIntoViewIfNeeded()
  await expect(doctorCard).toHaveClass(/reveal-in/)
  await expect(doctorCard).toHaveCSS('opacity', '1')
})

test.describe('Người dùng bật "giảm chuyển động"', () => {
  test.use({ reducedMotion: 'reduce' })

  test('không ẩn nội dung chờ cuộn, số liệu hiện ngay giá trị cuối, không có hoạt cảnh', async ({ page, request }) => {
    await page.goto('/')
    await expect(page.locator('[data-test="clinic-stats"]')).toBeVisible()
    expect(await page.locator('.reveal').count()).toBe(0)
    const clinic = await (await request.get('/api/v1/clinic')).json()
    await expect(page.locator('[data-test="stat-specialties"]')).toHaveText(String(clinic.specialtyCount))
    const animated = await page.evaluate(() =>
      [...document.querySelectorAll('body *')].filter((el) => {
        const style = getComputedStyle(el)
        return style.animationName !== 'none' && parseFloat(style.animationDuration) > 0.01
      }).length
    )
    expect(animated).toBe(0)
  })
})

test.describe('Điện thoại 390 px: thanh đặt lịch dính đáy trên trang bác sĩ', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('ẩn khi khối lịch khám đang hiện, hiện khi cuộn qua, không che nội dung và đặt được lịch', async ({ page }) => {
    await page.goto('/doctors/1')
    await expect(page.locator('[data-test="schedule-picker"]')).toBeVisible()
    const bar = page.locator('[data-test="mobile-booking-bar"]')
    await expect(bar).toHaveCount(0)

    await page.locator('footer').scrollIntoViewIfNeeded()
    await expect(bar).toBeVisible()
    await expect(bar.getByText('Bác sĩ Lan')).toBeVisible()
    await expect(bar.locator('[data-test="mobile-bar-slot"]')).toContainText('Sớm nhất')
    expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false)

    // Chân trang không bị thanh che: dòng cuối của chân trang nằm trên mép trên của thanh khi cuộn tới cuối
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
    const barBox = await bar.boundingBox()
    const lastLine = await page.locator('footer [data-test="data-notice"]').boundingBox()
    expect(lastLine.y + lastLine.height).toBeLessThanOrEqual(barBox.y)

    await bar.getByRole('link', { name: /^Đặt lịch / }).click()
    await expect(page).toHaveURL(/\/login\?redirect=.*slotId/)
  })

  test('từ 768 px trở lên không có thanh dính đáy', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 800 })
    await page.goto('/doctors/1')
    await page.locator('footer').scrollIntoViewIfNeeded()
    await expect(page.locator('[data-test="mobile-booking-bar"]')).toBeHidden()
  })
})

test('Bệnh nhân đặt lịch thành công: có dấu tích, tóm tắt và tải được tệp .ics đúng giờ', async ({ page, request }) => {
  // Ca làm việc riêng ở một ngày xa để không đụng dữ liệu của các kịch bản khác
  const doctorLogin = await request.post('/api/v1/auth/login', { data: { usernameOrEmail: 'doctor1', password: PASSWORD } })
  const doctor = { Authorization: `Bearer ${(await doctorLogin.json()).token}` }
  const day = isoDay(320 + Math.floor(Math.random() * 30))
  const schedule = await request.post('/api/v1/doctor-schedules', {
    headers: doctor,
    data: { workDate: day, startTime: '15:00', endTime: '15:30', slotMinutes: 30 }
  })
  expect(schedule.status()).toBe(201)
  const slots = await (await request.get(`/api/v1/doctors/1/slots?date=${day}`)).json()

  await login(page, 'patient1')
  await page.goto(`/patient/booking?doctorId=1&slotId=${slots[0].id}&date=${day}&start=15:00&end=15:30`)
  await expect(page.locator('[data-test="prechosen-slot"]')).toBeVisible()
  await page.locator('#booking-service').selectOption({ label: 'Khám tổng quát' })
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await page.getByRole('button', { name: 'Xác nhận đặt lịch' }).click()

  const done = page.locator('[data-test="done"]')
  await expect(done.getByRole('status')).toContainText('Đặt lịch thành công')
  await expect(done.locator('[data-test="success-check"]')).toBeVisible()
  await expect(done.locator('[data-test="success-summary"]')).toContainText('15:00–15:30')

  const download = page.waitForEvent('download')
  await done.getByRole('button', { name: 'Thêm vào lịch (.ics)' }).click()
  const file = await download
  expect(file.suggestedFilename()).toMatch(/^lich-kham-medbook-\d+\.ics$/)
  const ics = (await readFile(await file.path(), 'utf-8')).replace(/\r\n /g, '')
  expect(ics).toContain(`DTSTART;TZID=Asia/Ho_Chi_Minh:${day.replace(/-/g, '')}T150000`)
  expect(ics).toContain('SUMMARY:Khám tổng quát với Bác sĩ Lan (MedBook)')
  expect(ics).toContain('LOCATION:Phòng khám đa khoa MedBook')
})
