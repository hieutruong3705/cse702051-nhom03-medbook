import { expect, test } from '@playwright/test'
import { PASSWORD, dialog, isoDay, login } from './support'

// Chức năng bổ sung theo góp ý giảng viên (YCCN-35 đến 44) trên ứng dụng thật: khách xem chi tiết bác sĩ, chuyên
// khoa, giới thiệu phòng khám và đánh giá; bệnh nhân khai hồ sơ sức khỏe và đánh giá lần khám đã hoàn thành; bác sĩ
// sửa phần giới thiệu; quản trị viên ẩn đánh giá. Lần khám đã hoàn thành được dựng qua API thật (đặt lịch → bác sĩ
// khám → hoàn thành), không chèn thẳng vào CSDL.
test.describe.configure({ mode: 'serial' })

const COMMENT = `Bác sĩ khám kỹ, giải thích dễ hiểu (e2e ${Date.now()})`
const shared = { appointmentId: null }

async function apiLogin(request, username) {
  const response = await request.post('/api/v1/auth/login', { data: { usernameOrEmail: username, password: PASSWORD } })
  expect(response.ok()).toBeTruthy()
  return { Authorization: `Bearer ${(await response.json()).token}` }
}

/** patient1 đặt một khung giờ của doctor1 rồi doctor1 khám và hoàn thành, trả về mã lịch hẹn. */
async function completedVisit(request) {
  const doctor = await apiLogin(request, 'doctor1')
  const patient = await apiLogin(request, 'patient1')
  // Ca làm việc riêng ở một ngày xa để không đụng dữ liệu của các ca khác
  const day = isoDay(200 + Math.floor(Math.random() * 100))
  const schedule = await request.post('/api/v1/doctor-schedules', {
    headers: doctor,
    data: { workDate: day, startTime: '13:00', endTime: '13:30', slotMinutes: 30 }
  })
  expect(schedule.status()).toBe(201)
  const slots = await (await request.get(`/api/v1/doctors/1/slots?date=${day}`)).json()
  const booked = await request.post('/api/v1/appointments', { headers: patient, data: { slotId: slots[0].id, serviceId: 1 } })
  expect(booked.status()).toBe(201)
  const appointmentId = (await booked.json()).id
  const encounter = await request.post('/api/v1/encounters', {
    headers: doctor,
    data: { appointmentId, chiefComplaint: 'Khám theo hẹn (e2e)' }
  })
  expect(encounter.status()).toBe(201)
  const done = await request.put(`/api/v1/encounters/${(await encounter.json()).id}`, {
    headers: doctor,
    data: { diagnosis: 'Dữ liệu kiểm thử', status: 'COMPLETED' }
  })
  expect(done.ok()).toBeTruthy()
  return appointmentId
}

test('Khách xem chi tiết bác sĩ, chuyên khoa và giới thiệu phòng khám mà không cần đăng nhập', async ({ page }) => {
  await page.goto('/doctors')
  await page.getByRole('link', { name: 'Bác sĩ Lan', exact: true }).click()
  await expect(page).toHaveURL(/\/doctors\/1$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Bác sĩ Lan' })).toBeVisible()
  await expect(page.getByText('Thế mạnh chuyên môn')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Lịch khám', exact: true })).toBeVisible()
  await expect(page.locator('[data-test="slot-link"]').first()).toBeVisible()
  await expect(page).toHaveTitle('Bác sĩ Lan · MedBook')
  await expect(page.locator('body')).not.toContainText('CCHN-0001')

  await page.getByRole('link', { name: 'Nội tổng quát' }).first().click()
  await expect(page).toHaveURL(/\/specialties\/1$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Nội tổng quát' })).toBeVisible()

  await page.getByRole('navigation', { name: 'Chính' }).getByRole('link', { name: 'Giới thiệu' }).click()
  await expect(page.getByRole('heading', { name: 'Giờ làm việc' })).toBeVisible()
  await expect(page.getByText('Dữ liệu mô phỏng phục vụ mục đích học tập, không phải phòng khám thật.')).toBeVisible()
})

test('Trang chủ: đặt lịch nhanh tìm giờ sớm nhất, dải số liệu và băng chuyền điều khiển bằng bàn phím', async ({ page }) => {
  await page.goto('/')
  const quick = page.locator('[data-test="quick-booking"]')
  await quick.getByLabel('Chuyên khoa').selectOption({ label: 'Nội tổng quát' })
  // Chạy muộn trong ngày thì hôm nay có thể đã hết giờ: thử lần lượt các ngày tới khi có kết quả
  const first = quick.locator('[data-test="quick-slot"]').first()
  for (let index = 0; index < 7; index++) {
    await quick.getByLabel('Ngày khám').selectOption({ index })
    await quick.getByRole('button', { name: 'Tìm giờ trống' }).click()
    await expect(quick.getByRole('button', { name: 'Tìm giờ trống' })).toBeEnabled()
    await expect(quick.locator('[data-test="quick-results"]')).not.toBeEmpty()
    if (await first.count()) break
  }
  await expect(first).toBeVisible()
  await expect(first).toContainText('Bác sĩ Lan')
  await expect(first).toHaveAttribute('href', /^\/patient\/booking\?doctorId=1&slotId=\d+&date=/)

  await expect(page.locator('[data-test="stat-specialties"]')).toHaveText(/^\d+$/)

  const carousel = page.locator('[data-test="review-carousel"]')
  await expect(carousel).toBeVisible()
  const slide = carousel.locator('[data-test="carousel-slide"]').first()
  const before = await slide.getAttribute('aria-label')
  await carousel.getByRole('button', { name: 'Đánh giá sau' }).focus()
  await page.keyboard.press('Enter')
  await expect(slide).not.toHaveAttribute('aria-label', before)

  await first.click()
  await expect(page).toHaveURL(/\/login\?redirect=/)
})

test('Khách đổi ngày khám trên dải 7 ngày bằng bàn phím; khung giờ đầu có nhãn "Sớm nhất"', async ({ page }) => {
  await page.goto('/doctors/1')
  const group = page.getByRole('radiogroup', { name: 'Chọn ngày khám' })
  const days = group.getByRole('radio')
  await expect(days).toHaveCount(7)
  await expect(group.locator('[aria-checked="true"]')).toHaveCount(1)
  await expect(page.locator('[data-test="slot-link"]').first()).toContainText('Sớm nhất')

  const checked = group.locator('[aria-checked="true"]')
  const before = await checked.textContent()
  await checked.focus()
  const reload = page.waitForResponse((res) => res.url().includes('/api/v1/doctors/1/slots'))
  await page.keyboard.press('ArrowRight')
  await reload
  await expect(group.locator('[aria-checked="true"]')).not.toHaveText(before)
  // Ngày mới được chọn là phần tử đang có focus (radio: chọn theo focus)
  expect(await page.evaluate(() => document.activeElement?.getAttribute('aria-checked'))).toBe('true')
})

test('Bệnh nhân khai thông tin sức khỏe, có BMI tự tính, lưu được và tải lại vẫn còn', async ({ page }) => {
  await login(page, 'patient1')
  await page.goto('/patient/profile')
  await page.locator('#patient-blood-type').selectOption('O+')
  await page.locator('#patient-height').fill('170')
  await page.locator('#patient-weight').fill('65')
  await expect(page.locator('[data-test="bmi"]')).toContainText('22.5 (bình thường)')
  await page.locator('#patient-chronic').fill('Tăng huyết áp nhẹ (e2e)')
  await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
  await expect(page.getByText('Đã lưu hồ sơ.')).toBeVisible()
  await page.reload()
  await expect(page.locator('#patient-chronic')).toHaveValue('Tăng huyết áp nhẹ (e2e)')
})

test('Bệnh nhân đánh giá lần khám đã hoàn thành bằng bàn phím; đánh giá hiện trên trang bác sĩ với tên rút gọn', async ({ page, request }) => {
  shared.appointmentId = await completedVisit(request)
  await login(page, 'patient1')
  await page.goto(`/patient/appointments/${shared.appointmentId}`)
  await expect(page.getByRole('heading', { name: 'Đánh giá lần khám' })).toBeVisible()

  // Chọn sao bằng bàn phím: tiêu điểm vào nhóm radio rồi dùng phím mũi tên
  await page.locator('#visit-rating-3').focus()
  await page.keyboard.press('Space')
  await page.keyboard.press('ArrowRight')
  await expect(page.locator('#visit-rating-4')).toBeChecked()
  await page.locator('#visit-comment').fill(COMMENT)
  await page.getByRole('button', { name: 'Gửi đánh giá' }).click()
  await expect(page.locator('[data-test="review-view"]')).toContainText(COMMENT)

  await page.goto('/doctors/1')
  const review = page.getByRole('listitem').filter({ hasText: COMMENT })
  await expect(review).toBeVisible()
  await expect(review).toContainText('Nguyễn M. A.')
  await expect(review).not.toContainText('Nguyễn Minh An')
})

test('Bác sĩ sửa phần giới thiệu và trang công khai hiện ngay', async ({ page }) => {
  const expertise = `Khám nội tổng quát, tư vấn sức khỏe định kỳ (e2e ${Date.now()})`
  await login(page, 'doctor1')
  await page.goto('/doctor/profile')
  await page.locator('#doctor-expertise').fill(expertise)
  await page.getByRole('button', { name: 'Lưu hồ sơ' }).click()
  await expect(page.getByText('Đã lưu hồ sơ.')).toBeVisible()
  await page.goto('/doctor/reviews')
  await expect(page.getByText(COMMENT)).toBeVisible()
  await page.goto('/doctors/1')
  await expect(page.getByText(expertise)).toBeVisible()
})

test('Quản trị viên ẩn đánh giá kèm lý do: đánh giá biến khỏi trang công khai, bệnh nhân thấy lý do', async ({ page, browser }) => {
  await login(page, 'admin1')
  await page.goto('/admin/reviews')
  const row = page.getByRole('row').filter({ hasText: COMMENT })
  await row.getByRole('button', { name: 'Ẩn' }).click()
  await dialog(page).locator('#hide-reason').fill('Kiểm thử kiểm duyệt (e2e)')
  await dialog(page).getByRole('button', { name: 'Ẩn đánh giá' }).click()
  await expect(row).toContainText('Đã ẩn')

  const guest = await browser.newPage()
  await guest.goto('/doctors/1')
  await expect(guest.getByRole('heading', { level: 1, name: 'Bác sĩ Lan' })).toBeVisible()
  await expect(guest.getByText(COMMENT)).toHaveCount(0)
  await guest.close()

  const patient = await browser.newPage()
  await login(patient, 'patient1')
  await patient.goto(`/patient/appointments/${shared.appointmentId}`)
  await expect(patient.getByText('Quản trị viên đã ẩn đánh giá này: Kiểm thử kiểm duyệt (e2e)')).toBeVisible()
  await expect(patient.getByRole('button', { name: 'Sửa đánh giá' })).toHaveCount(0)
  await patient.close()
})
