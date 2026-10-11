import { expect, test } from '@playwright/test'
import { PASSWORD, dialog, isoDay, login, storedToken } from './support'

// Khu lễ tân trên ứng dụng thật: tạo hồ sơ bệnh nhân tại quầy → đặt lịch hộ → đổi lịch → hủy lịch; rồi thu tiền một
// hóa đơn do bác sĩ lập. Các ca chạy lần lượt và dùng chung ca làm việc được mở ở bước chuẩn bị.
test.describe.configure({ mode: 'serial' })

const DOCTOR_ID = '1' // doctor1 (Bác sĩ Lan)
// Mỗi lần chạy dùng một ngày khác nhau để không đụng ca làm việc của lần chạy trước.
const day = isoDay(250 + Math.floor(Math.random() * 100))
const marker = Date.now().toString().slice(-7)
const PATIENT_NAME = `Lê Thị Quầy ${marker}`
const shared = { invoiceCode: null }

/** Đăng nhập qua API (không qua giao diện) để chuẩn bị dữ liệu bằng đúng quyền của từng vai trò. */
async function apiToken(request, username) {
  const response = await request.post('/api/v1/auth/login', {
    data: { usernameOrEmail: username, password: PASSWORD }
  })
  expect(response.ok()).toBe(true)
  return (await response.json()).token
}

const auth = (token) => ({ Authorization: `Bearer ${token}` })

test('chuẩn bị: bác sĩ mở một ca làm việc bốn khung giờ', async ({ request }) => {
  const doctor = await apiToken(request, 'doctor1')
  const response = await request.post('/api/v1/doctor-schedules', {
    headers: auth(doctor),
    data: { workDate: day, startTime: '08:00:00', endTime: '10:00:00', slotMinutes: 30 }
  })
  expect(response.status()).toBe(201)
})

test('lễ tân tạo hồ sơ bệnh nhân, đặt lịch hộ, đổi lịch rồi hủy lịch', async ({ page }) => {
  await login(page, 'receptionist1')
  await expect(page).toHaveURL(/^[^?#]*\/receptionist\/dashboard/)
  await expect(page.getByRole('heading', { name: 'Tổng quan lễ tân' })).toBeVisible()
  await expect(page.getByText('Hóa đơn chưa thu')).toBeVisible()

  // Tạo hồ sơ cho bệnh nhân chưa có tài khoản
  await page.goto('/receptionist/patients')
  await page.getByRole('button', { name: 'Thêm bệnh nhân' }).click()
  await dialog(page).locator('#patient-full-name').fill(PATIENT_NAME)
  await dialog(page).locator('#patient-date-of-birth').fill('1988-03-15')
  await dialog(page).locator('#patient-gender').selectOption('FEMALE')
  await dialog(page).locator('#patient-phone').fill(`09${marker}1`)
  await dialog(page).getByRole('button', { name: 'Lưu' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)

  await page.locator('#patient-keyword').fill(PATIENT_NAME)
  await page.getByRole('button', { name: 'Tìm' }).click()
  const patientRow = page.getByRole('row').filter({ hasText: PATIENT_NAME })
  await expect(patientRow).toHaveCount(1)
  await expect(patientRow).toContainText('Không') // chưa có tài khoản đăng nhập

  // Đặt lịch hộ: dịch vụ → bác sĩ và giờ → xác nhận
  await patientRow.getByRole('link', { name: 'Đặt lịch' }).click()
  await expect(page).toHaveURL(/^[^?#]*\/receptionist\/booking\?patientId=\d+/)
  await page.locator('#booking-service').selectOption({ label: 'Khám tổng quát' }) // 30 phút, vừa khung giờ của ca
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await page.locator('#booking-doctor').selectOption(DOCTOR_ID)
  await page.locator('#booking-date').fill(day)
  const freeSlots = page.getByRole('list', { name: 'Khung giờ còn trống' }).getByRole('button')
  await expect(freeSlots).toHaveCount(4)
  await freeSlots.first().click()
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await expect(page.getByRole('heading', { name: 'Xác nhận đặt lịch' })).toBeVisible()
  await page.locator('#booking-notes').fill('Bệnh nhân đến quầy, nhờ đặt hộ')
  await page.getByRole('button', { name: 'Xác nhận đặt lịch' }).click()
  await expect(page.getByRole('heading', { name: 'Đã đặt lịch' })).toBeVisible()

  // Lịch vừa đặt xuất hiện ở danh sách của quầy
  await page.getByRole('link', { name: 'Về danh sách lịch hẹn' }).click()
  await page.locator('#appointment-from').fill(day)
  await page.locator('#appointment-to').fill(day)
  await page.getByRole('button', { name: 'Lọc' }).click()
  const row = page.getByRole('row').filter({ hasText: PATIENT_NAME })
  await expect(row).toHaveCount(1)
  await expect(row).toContainText('Đã đặt')
  await expect(row).toContainText('08:00')
  await expect(row).not.toContainText('nhờ đặt hộ') // ghi chú không hiện lại ở quầy

  // Đổi lịch hộ: khung giờ đang giữ không còn trong danh sách trống, chọn khung kế tiếp
  await row.getByRole('button', { name: /Đổi lịch #/ }).click()
  const otherSlots = dialog(page).getByRole('list', { name: 'Khung giờ còn trống' }).getByRole('button')
  await expect(otherSlots).toHaveCount(3)
  await otherSlots.first().click()
  await dialog(page).locator('#reschedule-reason').fill('Bệnh nhân xin lùi nửa giờ')
  await dialog(page).getByRole('button', { name: 'Đổi lịch', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(row).toContainText('08:30')
  await expect(row).toContainText('Đã đặt')

  // Hủy lịch hộ: bắt buộc có lý do
  await row.getByRole('button', { name: /Hủy lịch #/ }).click()
  await dialog(page).getByRole('button', { name: 'Hủy lịch hẹn' }).click()
  await expect(dialog(page)).toContainText('Phải nhập lý do')
  await dialog(page).locator('#cancel-reason').fill('Bệnh nhân báo bận')
  await dialog(page).getByRole('button', { name: 'Hủy lịch hẹn' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(row).toContainText('Đã hủy')
  await expect(row.getByRole('button')).toHaveCount(0)
})

test('lễ tân không đọc được dữ liệu lâm sàng và không vào được khu quản trị', async ({ page, request }) => {
  await login(page, 'receptionist1')
  const token = await storedToken(page)

  for (const path of ['/medical-records/me', '/encounters', '/patients', '/admin/users', '/admin/invoices']) {
    const response = await request.get(`/api/v1${path}`, { headers: auth(token) })
    expect(response.status(), path).toBe(403)
  }

  await page.goto('/admin/dashboard')
  await expect(page).toHaveURL(/^[^?#]*\/403/)
  await page.goto('/doctor/dashboard')
  await expect(page).toHaveURL(/^[^?#]*\/403/)
})

test('chuẩn bị: bệnh nhân đặt lịch, bác sĩ khám và lập hóa đơn', async ({ request }) => {
  const patient = await apiToken(request, 'patient1')
  const doctor = await apiToken(request, 'doctor1')
  const slots = await (await request.get(`/api/v1/doctors/${DOCTOR_ID}/slots?date=${day}`)).json()
  expect(slots.length).toBeGreaterThan(0)

  const booked = await request.post('/api/v1/appointments', {
    headers: auth(patient),
    data: { slotId: slots[0].id, serviceId: 1 }
  })
  expect(booked.status()).toBe(201)
  const encounter = await request.post('/api/v1/encounters', {
    headers: auth(doctor),
    data: { appointmentId: (await booked.json()).id }
  })
  expect(encounter.ok()).toBe(true)
  const invoice = await request.post(`/api/v1/encounters/${(await encounter.json()).id}/invoice`, {
    headers: auth(doctor),
    data: [{ serviceId: 1, quantity: 1 }]
  })
  expect(invoice.ok()).toBe(true)
  shared.invoiceCode = (await invoice.json()).invoiceCode
})

test('lễ tân xem dòng hóa đơn và ghi nhận đã thu; hóa đơn rời khỏi danh sách chưa thu', async ({ page }) => {
  expect(shared.invoiceCode, 'ca chuẩn bị phải lập được hóa đơn').toBeTruthy()
  await login(page, 'receptionist1')
  await page.goto('/receptionist/invoices')
  await page.locator('#invoice-keyword').fill(shared.invoiceCode)
  await page.getByRole('button', { name: 'Lọc' }).click()
  const row = page.getByRole('row').filter({ hasText: shared.invoiceCode })
  await expect(row).toHaveCount(1)
  await expect(row).toContainText('Chưa thu')

  // Chi tiết: có dòng dịch vụ và tổng tiền
  await row.getByRole('button', { name: shared.invoiceCode, exact: true }).click()
  await expect(dialog(page)).toContainText('Thành tiền')
  await expect(dialog(page)).toContainText('Tổng tiền')
  // hộp thoại có hai nút Đóng (dấu x ở góc và nút ở chân); bấm nút ở chân
  await dialog(page).getByRole('button', { name: 'Đóng' }).last().click()
  await expect(page.getByRole('dialog')).toHaveCount(0)

  // Thu tiền qua hộp xác nhận
  await row.getByRole('button', { name: `Thu tiền ${shared.invoiceCode}` }).click()
  await expect(dialog(page)).toContainText(shared.invoiceCode)
  await dialog(page).getByRole('button', { name: 'Đã thu' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('row').filter({ hasText: shared.invoiceCode })).toHaveCount(0)

  await page.locator('#invoice-status').selectOption('PAID')
  await page.getByRole('button', { name: 'Lọc' }).click()
  const paidRow = page.getByRole('row').filter({ hasText: shared.invoiceCode })
  await expect(paidRow).toContainText('Đã thu')
  await expect(paidRow.getByRole('button', { name: /Thu tiền/ })).toHaveCount(0)
})
