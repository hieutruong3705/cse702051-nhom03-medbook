import { expect, test } from '@playwright/test'

// Tài khoản demo của chế độ phát triển (data-h2.sql); mật khẩu ghi trong README.
const PASSWORD = 'MedBook@2026'
const unique = () => Date.now().toString(36).toUpperCase()
const isoDay = (offsetDays) => {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const viDay = (iso) => {
  const [year, month, day] = iso.split('-').map(Number)
  return `${day}/${month}/${year}`
}

async function login(page, username) {
  await page.goto('/#/login')
  await page.locator('#login-identity').fill(username)
  await page.locator('#login-password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible()
}

const dialog = (page) => page.getByRole('dialog').last()

test.describe('Trang công khai và bảo mật cơ bản', () => {
  test('khách xem được danh sách bác sĩ; bấm Đặt lịch thì phải đăng nhập', async ({ page }) => {
    await page.goto('/#/doctors')
    await expect(page.getByRole('heading', { name: 'Bác sĩ', level: 1 })).toBeVisible()
    await expect(page.getByText('Bác sĩ Lan').first()).toBeVisible()
    await expect(page.getByRole('table').getByText('Nội tổng quát').first()).toBeVisible()
    await expect(page.locator('footer')).toContainText('Dữ liệu mô phỏng phục vụ mục đích học tập')
    await expect(page.locator('footer')).toContainText('Hệ thống quản lý bệnh án và đặt lịch khám bệnh')

    await page.getByRole('link', { name: 'Đặt lịch với Bác sĩ Lan' }).click()
    await expect(page).toHaveURL(/#\/login/)
    await expect(page.locator('#login-identity')).toBeVisible()
  })

  test('phản hồi có đủ header bảo mật; robots.txt và sitemap.xml truy cập được; API cần đăng nhập trả 401', async ({ request }) => {
    const home = await request.get('/')
    expect(home.status()).toBe(200)
    const headers = home.headers()
    expect(headers['content-security-policy']).toContain("default-src 'self'")
    expect(headers['x-frame-options']).toBe('DENY')
    expect(headers['x-content-type-options']).toBe('nosniff')
    expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin')
    expect(headers['permissions-policy']).toContain('camera=()')

    expect((await request.get('/robots.txt')).status()).toBe(200)
    expect((await request.get('/api/v1/health')).status()).toBe(200)
    expect(await (await request.get('/sitemap.xml')).text()).toContain('<urlset')

    const anonymous = await request.get('/api/v1/admin/users')
    expect(anonymous.status()).toBe(401)
    expect((await anonymous.json()).code).toBe('UNAUTHORIZED')
    expect((await anonymous.json()).requestId).toBe(anonymous.headers()['x-request-id'])
    expect((await request.get('/v3/api-docs')).status()).toBe(401)
  })

  test('sai mật khẩu báo lỗi chung, không lộ tài khoản có tồn tại hay không', async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('#login-identity').fill('patient1')
    await page.locator('#login-password').fill('Sai#Mat1khau')
    await page.getByRole('button', { name: 'Đăng nhập' }).click()
    await expect(page.getByText('Tên đăng nhập/email hoặc mật khẩu không chính xác!')).toBeVisible()
    await expect(page).toHaveURL(/#\/login/)
  })
})

test.describe('Bệnh nhân', () => {
  test('đăng nhập chỉ bằng bàn phím, vào tổng quan, mở thông báo từ chuông và đăng xuất', async ({ page }) => {
    await page.goto('/#/login')
    await page.locator('#login-identity').focus()
    await page.keyboard.type('patient1')
    await page.keyboard.press('Tab')
    await page.keyboard.type(PASSWORD)
    await page.keyboard.press('Enter')

    await expect(page).toHaveURL(/#\/patient\/dashboard/)
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Xin chào')

    await page.getByTestId('notification-bell').or(page.locator('[data-test="notification-bell"]')).first().click()
    await expect(page).toHaveURL(/#\/patient\/notifications/)
    await expect(page.getByRole('heading', { name: 'Thông báo', level: 1 })).toBeVisible()

    await page.getByRole('button', { name: 'Đăng xuất' }).click()
    await expect(page.getByRole('button', { name: 'Đăng xuất' })).toHaveCount(0)
  })

  test('không vào được khu quản trị: giao diện báo không có quyền và API trả 403', async ({ page }) => {
    await login(page, 'patient1')
    await page.goto('/#/admin/users')
    await expect(page.getByText('Bạn không có quyền truy cập')).toBeVisible()
    await expect(page.getByText('Tạo tài khoản')).toHaveCount(0)

    const token = await page.evaluate(async () => {
      const response = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usernameOrEmail: 'patient1', password: 'MedBook@2026' })
      })
      return (await response.json()).token
    })
    const forbidden = await page.request.get('/api/v1/admin/users', { headers: { Authorization: `Bearer ${token}` } })
    expect(forbidden.status()).toBe(403)
  })
})

test.describe('Bác sĩ', () => {
  test('tạo ca làm việc, xem slot, thêm giờ nghỉ, chặn ca chồng giờ, rồi xóa ca', async ({ page }) => {
    const day = isoDay(40 + Math.floor(Math.random() * 15))
    await login(page, 'doctor1')
    await page.goto('/#/doctor/schedules')
    await expect(page.getByRole('heading', { name: 'Lịch làm việc', level: 1 })).toBeVisible()

    await page.locator('#schedule-date').fill(day)
    await page.locator('#schedule-start').fill('18:00')
    await page.locator('#schedule-end').fill('20:00')
    await expect(page.getByText('Xem trước: 4 slot')).toBeVisible()
    await page.getByRole('button', { name: 'Tạo ca' }).click()
    await expect(page.getByText('Đã tạo ca làm việc')).toBeVisible()

    const row = page.getByRole('row').filter({ hasText: viDay(day) }).filter({ hasText: '18:00' })
    await expect(row).toContainText('0/4 đã đặt')

    // tạo lại đúng ca đó: máy chủ từ chối vì chồng giờ (422)
    await page.getByRole('button', { name: 'Tạo ca' }).click()
    await expect(page.getByRole('alert').filter({ hasText: 'chồng giờ' })).toBeVisible()

    await row.getByRole('button', { name: /Thêm giờ nghỉ cho ca/ }).click()
    await dialog(page).locator('#new-break-start').fill('19:00')
    await dialog(page).locator('#new-break-end').fill('19:30')
    await dialog(page).getByRole('button', { name: 'Thêm', exact: true }).click()
    await expect(row).toContainText('0/3 đã đặt')
    await expect(row).toContainText('19:00–19:30')

    await row.getByRole('button', { name: /Xem slot của ca/ }).click()
    await expect(dialog(page).getByText('Còn trống')).toHaveCount(3)
    await dialog(page).getByRole('button', { name: 'Đóng', exact: true }).last().click()

    await row.getByRole('button', { name: /Xóa ca/ }).click()
    await dialog(page).getByRole('button', { name: 'Xóa ca' }).click()
    await expect(page.getByText('Đã xóa ca làm việc')).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: viDay(day) }).filter({ hasText: '18:00' })).toHaveCount(0)
  })
})

test.describe('Quản trị viên', () => {
  test('thêm chuyên khoa mới và thấy ngay ở trang công khai, rồi xóa', async ({ page }) => {
    const code = `E2E${unique()}`
    const name = `Chuyên khoa thử ${code}`
    await login(page, 'admin1')
    await page.goto('/#/admin/catalog')
    await page.getByRole('button', { name: 'Thêm chuyên khoa' }).click()
    await dialog(page).locator('#catalog-code').fill(code)
    await dialog(page).locator('#catalog-name').fill(name)
    await dialog(page).getByRole('button', { name: 'Lưu' }).click()
    await expect(page.getByText('Đã thêm chuyên khoa')).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: code })).toBeVisible()

    await page.goto('/#/specialties')
    await expect(page.getByRole('heading', { name })).toBeVisible()

    await page.goto('/#/admin/catalog')
    await page.getByRole('button', { name: `Xóa ${name}` }).click()
    await dialog(page).getByRole('button', { name: 'Xóa', exact: true }).click()
    await expect(page.getByText('Đã xóa chuyên khoa')).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: code })).toHaveCount(0)
  })

  test('tạo tài khoản bệnh nhân, khóa rồi mở khóa; người bị khóa không đăng nhập được', async ({ page, browser }) => {
    const username = `e2e_${unique().toLowerCase()}`
    await login(page, 'admin1')
    await page.goto('/#/admin/users')
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click()
    await dialog(page).locator('#create-full-name').fill('Bệnh nhân Thử Nghiệm')
    await dialog(page).locator('#create-email').fill(`${username}@example.com`)
    await dialog(page).locator('#create-username').fill(username)
    await dialog(page).locator('#create-password').fill(PASSWORD)
    await dialog(page).getByRole('button', { name: 'Tạo', exact: true }).click()
    await expect(page.getByText('Đã tạo tài khoản')).toBeVisible()

    await page.locator('#admin-user-keyword').fill(username)
    await page.getByRole('button', { name: 'Lọc' }).click()
    const row = page.getByRole('row').filter({ hasText: username })
    await expect(row).toContainText('Bệnh nhân')
    await expect(row).toContainText('Hoạt động')

    await row.getByRole('button', { name: `Khóa tài khoản ${username}` }).click()
    await dialog(page).locator('#user-status-reason').fill('Kiểm thử khóa tài khoản')
    await dialog(page).getByRole('button', { name: 'Khóa tài khoản' }).click()
    await expect(row).toContainText('Đã khóa')

    const other = await browser.newContext()
    const guest = await other.newPage()
    await guest.goto('/#/login')
    await guest.locator('#login-identity').fill(username)
    await guest.locator('#login-password').fill(PASSWORD)
    await guest.getByRole('button', { name: 'Đăng nhập' }).click()
    await expect(guest.getByText(/đã bị khóa/)).toBeVisible()
    await other.close()

    await row.getByRole('button', { name: `Mở khóa tài khoản ${username}` }).click()
    await dialog(page).getByRole('button', { name: 'Mở khóa' }).click()
    await expect(row).toContainText('Hoạt động')
  })

  test('ba báo cáo có biểu đồ và bảng số liệu; xuất CSV tải được tệp', async ({ page }) => {
    await login(page, 'admin1')
    await page.goto('/#/admin/reports')
    await expect(page.getByRole('heading', { name: 'Lịch khám', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Doanh thu', exact: true })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Dịch vụ khám', exact: true })).toBeVisible()
    await expect(page.getByText('Tổng lịch khám')).toBeVisible()
    await expect(page.getByText('Giá trị lập hóa đơn').first()).toBeVisible()

    for (const [button, prefix] of [
      ['Xuất CSV lịch khám', 'medbook-lich-kham-'],
      ['Xuất CSV doanh thu', 'medbook-doanh-thu-'],
      ['Xuất CSV dịch vụ', 'medbook-dich-vu-']
    ]) {
      const [download] = await Promise.all([
        page.waitForEvent('download'),
        page.getByRole('button', { name: button }).click()
      ])
      expect(download.suggestedFilename()).toContain(prefix)
      expect(download.suggestedFilename()).toMatch(/\.csv$/)
    }
  })

  test('nhật ký hệ thống ghi nhận đăng nhập và tra cứu được theo mã hành động', async ({ page }) => {
    await login(page, 'admin1')
    await page.goto('/#/admin/audit-logs')
    await expect(page.getByRole('heading', { name: 'Nhật ký hệ thống', level: 1 })).toBeVisible()
    await page.locator('#audit-action').selectOption('LOGIN_SUCCESS')
    await page.getByRole('button', { name: 'Lọc' }).click()
    await expect(page.getByRole('cell', { name: 'LOGIN_SUCCESS' }).first()).toBeVisible()
  })
})

test.describe('Màn hình hẹp 360 px', () => {
  const pages = {
    patient1: ['/#/patient/dashboard', '/#/patient/booking', '/#/patient/appointments', '/#/patient/notifications', '/#/patient/invoices'],
    doctor1: ['/#/doctor/dashboard', '/#/doctor/schedules', '/#/doctor/appointments', '/#/doctor/patients', '/#/doctor/notifications'],
    admin1: ['/#/admin/dashboard', '/#/admin/users', '/#/admin/doctors', '/#/admin/catalog', '/#/admin/invoices', '/#/admin/reports', '/#/admin/audit-logs']
  }

  for (const [username, routes] of Object.entries(pages)) {
    test(`${username}: các trang không cuộn ngang, menu di động có đăng xuất`, async ({ page }) => {
      await page.setViewportSize({ width: 1366, height: 800 })
      await login(page, username)
      await page.setViewportSize({ width: 360, height: 740 })
      for (const route of ['/#/doctors', ...routes]) {
        await page.goto(route)
        await page.waitForLoadState('networkidle')
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
        expect(overflow, `tràn ngang ở ${route}`).toBeLessThanOrEqual(0)
      }
      await page.getByRole('button', { name: 'Mở menu' }).click()
      await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible()
    })
  }
})
