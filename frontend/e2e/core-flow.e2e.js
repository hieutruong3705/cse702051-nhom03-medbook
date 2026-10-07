import { expect, test } from '@playwright/test'
import { PASSWORD, dialog, isoDay, login, openSession, storedToken, tabTo } from './support'

// Luồng nghiệp vụ chính trên ứng dụng thật: bác sĩ mở ca → bệnh nhân đặt lịch → bác sĩ khám, kê đơn, đính kèm tệp,
// lập hóa đơn → bệnh nhân xem kết quả → quản trị viên ghi nhận đã thu. Các ca chạy lần lượt và dùng chung dữ liệu
// của ca trước (cùng thứ tự bước với CoreFlowsAcceptanceTest ở backend).
test.describe.configure({ mode: 'serial' })

const DOCTOR = 'Bác sĩ Lan' // doctor1
const PATIENT = 'Nguyễn Minh An' // patient1
const FIRST_SLOT = '09:00 – 09:30'
const SECOND_SLOT = '09:30 – 10:00'
const DIAGNOSIS = 'Viêm họng cấp, theo dõi thêm ba ngày'
const MEDICINE = 'Paracetamol 500mg'
const RESULT_FILE = 'ket-qua-xet-nghiem.png'

// Ảnh PNG 1×1 hợp lệ.
const PNG_BYTES = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64'
)
// Tệp chương trình Windows (bắt đầu bằng "MZ") đội lốt ảnh.
const EXE_BYTES = Buffer.concat([Buffer.from('MZ'), Buffer.alloc(256, 0x90)])

// Mỗi lần chạy dùng một ngày khác nhau để không đụng ca làm việc của lần chạy trước.
const day = isoDay(60 + Math.floor(Math.random() * 180))
const shared = { encounterId: null, invoiceCode: null }

/** Đi các bước 1–4 của trang đặt lịch bằng chuột và dừng ở bước xác nhận. */
async function fillBookingUntilConfirm(page, slot) {
  await page.goto('/#/patient/booking')
  await page.getByRole('list', { name: 'Danh sách bác sĩ' }).getByRole('button', { name: new RegExp(DOCTOR) }).click()
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await page.locator('#booking-date').fill(day)
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await page.getByRole('button', { name: slot, exact: true }).click()
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await page.locator('#booking-service').selectOption({ index: 1 })
  await page.getByRole('button', { name: 'Tiếp tục' }).click()
  await expect(page.getByRole('heading', { name: 'Bước 5: Xác nhận thông tin' })).toBeVisible()
}

/**
 * Gõ ngày vào ô ngày đang có tiêu điểm. Thứ tự các phần (ngày/tháng/năm hay tháng/ngày/năm) do ngôn ngữ của trình
 * duyệt quyết định, nên thử lần lượt từng thứ tự; giữa hai lần thử, lùi ra khỏi ô bằng Shift+Tab rồi Tab vào lại
 * để con trỏ về phần đầu của ô.
 */
async function typeDate(page, input, iso) {
  const [year, month, dayOfMonth] = iso.split('-')
  const focused = () => input.evaluate((element) => element === document.activeElement)
  for (const digits of [`${dayOfMonth}${month}${year}`, `${month}${dayOfMonth}${year}`]) {
    await page.keyboard.type(digits)
    if ((await input.inputValue()) === iso) return
    for (let back = 0; back < 4 && (await focused()); back += 1) await page.keyboard.press('Shift+Tab')
    await page.keyboard.press('Tab')
    expect(await focused()).toBe(true)
  }
  throw new Error(`Không gõ được ngày ${iso} bằng bàn phím (giá trị hiện tại: ${await input.inputValue()})`)
}

test('bác sĩ mở ca; hai bệnh nhân cùng đặt một khung giờ thì người đến sau được báo và phải chọn lại', async ({ page, browser }) => {
  await login(page, 'doctor1')
  await page.goto('/#/doctor/schedules')
  await page.locator('#schedule-date').fill(day)
  await page.locator('#schedule-start').fill('09:00')
  await page.locator('#schedule-end').fill('10:00')
  await expect(page.getByText('Xem trước: 2 slot')).toBeVisible()
  await page.getByRole('button', { name: 'Tạo ca' }).click()
  await expect(page.getByText('Đã tạo ca làm việc')).toBeVisible()

  const winner = await openSession(browser, 'patient2')
  const loser = await openSession(browser, 'patient1')
  await fillBookingUntilConfirm(winner.page, SECOND_SLOT)
  await fillBookingUntilConfirm(loser.page, SECOND_SLOT)

  await winner.page.getByRole('button', { name: 'Xác nhận đặt lịch' }).click()
  await expect(winner.page.locator('[data-test="done"]')).toContainText('Đặt lịch thành công')

  await loser.page.getByRole('button', { name: 'Xác nhận đặt lịch' }).click()
  await expect(loser.page.locator('[data-test="conflict"]')).toBeVisible()
  await expect(loser.page.locator('[data-test="done"]')).toHaveCount(0)
  await expect(loser.page.getByRole('heading', { name: 'Bước 3: Chọn khung giờ' })).toBeVisible()
  // danh sách giờ trống được tải lại: giờ vừa bị người khác đặt đã biến mất, giờ còn lại vẫn chọn được
  await expect(loser.page.getByRole('button', { name: FIRST_SLOT, exact: true })).toBeVisible()
  await expect(loser.page.getByRole('button', { name: SECOND_SLOT, exact: true })).toHaveCount(0)

  await winner.context.close()
  await loser.context.close()
})

test('bệnh nhân đặt lịch từ đầu đến cuối chỉ bằng bàn phím', async ({ page }) => {
  await page.goto('/#/login')
  await page.locator('#login-identity').focus()
  await page.keyboard.type('patient1')
  await page.keyboard.press('Tab')
  await page.keyboard.type(PASSWORD)
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/#\/patient\/dashboard/)

  await page.goto('/#/patient/booking')
  const next = page.getByRole('button', { name: 'Tiếp tục' })

  // Bước 1: tìm và chọn bác sĩ
  await tabTo(page, page.locator('#booking-doctor-search'))
  await page.keyboard.type(DOCTOR)
  await tabTo(page, page.getByRole('list', { name: 'Danh sách bác sĩ' }).getByRole('button', { name: new RegExp(DOCTOR) }))
  await page.keyboard.press('Enter')
  await tabTo(page, next)
  await page.keyboard.press('Enter')

  // Bước 2: gõ ngày khám
  await expect(page.getByRole('heading', { name: 'Bước 2: Chọn ngày khám' })).toBeFocused()
  await tabTo(page, page.locator('#booking-date'))
  await typeDate(page, page.locator('#booking-date'), day)
  await tabTo(page, next)
  await page.keyboard.press('Enter')

  // Bước 3: chọn khung giờ còn trống
  await tabTo(page, page.getByRole('button', { name: FIRST_SLOT, exact: true }))
  await page.keyboard.press('Enter')
  await tabTo(page, next)
  await page.keyboard.press('Enter')

  // Bước 4: chọn dịch vụ bằng phím mũi tên, gõ ghi chú
  await tabTo(page, page.locator('#booking-service'))
  await page.keyboard.press('ArrowDown')
  await expect(page.getByText('Giá tham khảo:')).toBeVisible()
  await tabTo(page, page.locator('#booking-notes'))
  await page.keyboard.type('Đau họng ba ngày, sốt nhẹ')
  await tabTo(page, next)
  await page.keyboard.press('Enter')

  // Bước 5: xác nhận
  const summary = page.locator('[data-test="summary"]')
  await expect(summary).toContainText(DOCTOR)
  await expect(summary).toContainText(FIRST_SLOT)
  await tabTo(page, page.getByRole('button', { name: 'Xác nhận đặt lịch' }))
  await page.keyboard.press('Enter')
  await expect(page.locator('[data-test="done"]')).toContainText('Đặt lịch thành công')

  // lịch vừa đặt xuất hiện trong danh sách của bệnh nhân và bác sĩ được báo
  await tabTo(page, page.getByRole('link', { name: 'Xem chi tiết lịch hẹn' }))
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/#\/patient\/appointments\/\d+/)
  await expect(page.getByText('Đã đặt').first()).toBeVisible()
})

test('bác sĩ khám: lưu nháp, kê đơn, chặn tệp sai loại, đính kèm tệp, lập hóa đơn rồi hoàn thành', async ({ page }) => {
  await login(page, 'doctor1')
  await page.goto('/#/doctor/appointments')
  await page.locator('#doctor-appointment-from').fill(day)
  await page.locator('#doctor-appointment-to').fill(day)
  await page.getByRole('button', { name: 'Lọc' }).click()
  const row = page.getByRole('row').filter({ hasText: PATIENT })
  await expect(row).toContainText('Đau họng ba ngày, sốt nhẹ')
  await row.getByRole('button', { name: 'Bắt đầu khám' }).click()

  await expect(page).toHaveURL(/#\/doctor\/encounters\/\d+/)
  shared.encounterId = page.url().match(/encounters\/(\d+)/)[1]
  await expect(page.locator('[data-test="encounter-summary"]')).toContainText(PATIENT)

  // Tab Khám: lưu nháp
  await page.locator('#encounter-chiefComplaint').fill('Đau họng, sốt nhẹ ba ngày')
  await page.locator('#encounter-diagnosis').fill(DIAGNOSIS)
  await page.locator('[data-test="save-button"]').click()
  await expect(page.locator('[data-test="save-button"]')).toBeDisabled()
  await expect(page.locator('[data-test="form-error"]')).toHaveCount(0)

  // Tab Đơn thuốc
  await page.locator('[data-test="tab-prescriptions"]').click()
  await page.locator('[data-test="create-prescription"]').click()
  await expect(page.getByText('Đã tạo đơn thuốc')).toBeVisible()
  const itemForm = page.locator('[data-test^="item-form-"]')
  // thuốc chọn từ danh mục do Quản trị viên quản lý; máy chủ chép tên thuốc vào đơn
  await itemForm.locator('select[id$="-medicine"]').selectOption({ label: `${MEDICINE} (Viên)` })
  await expect(itemForm.getByLabel('Tên thuốc')).toHaveCount(0)
  await itemForm.getByLabel('Số lượng').fill('10')
  await itemForm.getByLabel('Số ngày dùng').fill('5')
  await itemForm.getByLabel('Liều dùng').fill('1 viên')
  await itemForm.getByLabel('Tần suất').fill('2 lần/ngày')
  await itemForm.getByLabel('Hướng dẫn').fill('Uống sau ăn')
  await itemForm.getByRole('button', { name: 'Thêm vào đơn' }).click()
  await expect(page.locator('[data-test^="item-"]').filter({ hasText: MEDICINE }).first()).toBeVisible()

  // Tab Tệp đính kèm: tệp sai loại bị chặn ở trình duyệt, tệp đội lốt ảnh bị máy chủ từ chối
  await page.locator('[data-test="tab-attachments"]').click()
  const fileInput = page.locator('#encounter-file')
  await fileInput.setInputFiles({ name: 'cai-dat.exe', mimeType: 'application/x-msdownload', buffer: EXE_BYTES })
  await expect(page.getByText('Chỉ hỗ trợ PDF, JPG hoặc PNG.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tải lên' })).toBeDisabled()

  await fileInput.setInputFiles({ name: 'anh-gia.png', mimeType: 'image/png', buffer: EXE_BYTES })
  await page.getByRole('button', { name: 'Tải lên' }).click()
  await expect(page.getByText('Chỉ hỗ trợ tệp PDF, JPG hoặc PNG.')).toBeVisible()
  await expect(page.locator('[data-test="att-list"]')).toHaveCount(0)

  await fileInput.setInputFiles({ name: RESULT_FILE, mimeType: 'image/png', buffer: PNG_BYTES })
  await page.getByRole('button', { name: 'Tải lên' }).click()
  await expect(page.getByText('Đã tải tệp lên')).toBeVisible()
  await expect(page.locator('[data-test="att-list"]')).toContainText(RESULT_FILE)

  // Tab Hóa đơn
  await page.locator('[data-test="tab-invoice"]').click()
  await page.locator('#inv-service-0').selectOption({ index: 1 })
  await page.getByRole('button', { name: 'Lập hóa đơn' }).click()
  const invoice = page.locator('[data-test="invoice-view"]')
  await expect(invoice).toContainText('Chưa thu')
  shared.invoiceCode = (await invoice.getByRole('heading').first().innerText()).replace('Hóa đơn', '').trim()
  expect(shared.invoiceCode).not.toBe('')

  // Hoàn thành: nội dung bị khóa
  await page.locator('[data-test="tab-exam"]').click()
  await page.locator('[data-test="complete-button"]').click()
  await dialog(page).getByRole('button', { name: 'Hoàn thành khám' }).click()
  await expect(page.locator('[data-test="readonly-banner"]')).toBeVisible()

  // lịch đã hoàn thành: chuyển ngược trạng thái là không hợp lệ, máy chủ trả 422
  const token = await storedToken(page)
  const appointmentId = (await page.locator('[data-test="encounter-summary"]').innerText()).match(/#(\d+)/)[1]
  const backwards = await page.request.patch(`/api/v1/appointments/${appointmentId}/status`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { status: 'IN_PROGRESS' }
  })
  expect(backwards.status()).toBe(422)
  expect((await backwards.json()).requestId).toBe(backwards.headers()['x-request-id'])
  await expect(page.locator('#encounter-diagnosis')).toBeDisabled()
  await expect(page.locator('[data-test="complete-button"]')).toHaveCount(0)
})

test('bác sĩ khác và bệnh nhân khác không xem được lần khám này', async ({ browser }) => {
  const otherDoctor = await openSession(browser, 'doctor2')
  await otherDoctor.page.goto(`/#/doctor/encounters/${shared.encounterId}`)
  await expect(otherDoctor.page.locator('[data-test="load-error"]')).toContainText('Bạn không phụ trách lần khám này')
  await expect(otherDoctor.page.getByText(DIAGNOSIS)).toHaveCount(0)
  const doctorToken = await storedToken(otherDoctor.page)
  const asDoctor = await otherDoctor.page.request.get(`/api/v1/encounters/${shared.encounterId}`, {
    headers: { Authorization: `Bearer ${doctorToken}` }
  })
  expect(asDoctor.status()).toBe(403)
  await otherDoctor.context.close()

  const otherPatient = await openSession(browser, 'patient2')
  await otherPatient.page.goto(`/#/patient/records/${shared.encounterId}`)
  await expect(otherPatient.page.locator('[data-test="load-error"]')).toBeVisible()
  await expect(otherPatient.page.getByText(DIAGNOSIS)).toHaveCount(0)
  await otherPatient.context.close()
})

test('bệnh nhân xem kết quả khám, đơn thuốc, tệp và hóa đơn của mình', async ({ page }) => {
  await login(page, 'patient1')
  await page.goto('/#/patient/records')
  await page.locator(`[data-test="encounter-link-${shared.encounterId}"]`).click()
  await expect(page).toHaveURL(new RegExp(`#/patient/records/${shared.encounterId}$`))
  await expect(page.getByRole('heading', { name: 'Kết quả khám' })).toBeVisible()
  await expect(page.locator('[data-test="field-diagnosis"]')).toContainText(DIAGNOSIS)
  await expect(page.locator('[data-test="prescriptions"]')).toContainText(MEDICINE)
  await expect(page.locator('[data-test="attachments"]')).toContainText(RESULT_FILE)
  await expect(page.locator('[data-test="invoice"]')).toContainText(shared.invoiceCode)

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('[data-test="attachments"]').getByRole('button', { name: 'Tải xuống' }).click()
  ])
  expect(download.suggestedFilename()).toBe(RESULT_FILE)

  await page.goto('/#/patient/invoices')
  await expect(page.getByRole('row').filter({ hasText: shared.invoiceCode })).toContainText('Chưa thu')
})

test('quản trị viên ghi nhận đã thu; bệnh nhân thấy hóa đơn đã thu', async ({ page, browser }) => {
  await login(page, 'admin1')
  await page.goto('/#/admin/invoices')
  await page.locator('#admin-invoice-keyword').fill(shared.invoiceCode)
  await page.getByRole('button', { name: 'Lọc' }).click()
  const row = page.getByRole('row').filter({ hasText: shared.invoiceCode })
  await expect(row).toContainText('Chưa thu')
  await row.getByRole('button', { name: `Ghi nhận đã thu ${shared.invoiceCode}` }).click()
  await dialog(page).getByRole('button', { name: 'Đã thu', exact: true }).click()
  await expect(row).toContainText('Đã thu')
  // hóa đơn đã thu thì không thu lại hay hủy được nữa
  await expect(row.getByRole('button', { name: `Ghi nhận đã thu ${shared.invoiceCode}` })).toBeDisabled()
  await expect(row.getByRole('button', { name: `Hủy hóa đơn ${shared.invoiceCode}` })).toBeDisabled()

  const patient = await openSession(browser, 'patient1')
  await patient.page.goto('/#/patient/invoices')
  await expect(patient.page.getByRole('row').filter({ hasText: shared.invoiceCode })).toContainText('Đã thu')
  await patient.context.close()
})

test('phiên bị thu hồi: token cũ nhận 401 và giao diện đưa người dùng về trang đăng nhập', async ({ page }) => {
  await login(page, 'patient2')
  const token = await storedToken(page)
  const headers = { Authorization: `Bearer ${token}` }

  // phiên bị kết thúc ở nơi khác (đăng xuất từ thiết bị khác) trong khi trang này vẫn giữ token cũ
  expect((await page.request.post('/api/v1/auth/logout', { headers })).status()).toBe(204)
  expect((await page.request.get('/api/v1/users/me', { headers })).status()).toBe(401)

  await page.goto('/#/patient/appointments')
  await expect(page).toHaveURL(/#\/login/)
  await expect(page.getByRole('status').filter({ hasText: 'Bạn cần đăng nhập để tiếp tục.' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Đăng xuất' })).toHaveCount(0)

  // đăng nhập lại thì được đưa về đúng trang đang xem dở
  await page.locator('#login-identity').fill('patient2')
  await page.locator('#login-password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await expect(page).toHaveURL(/#\/patient\/appointments/)
  await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible()
})
