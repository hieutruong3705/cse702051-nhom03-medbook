import { expect } from '@playwright/test'

// Tài khoản demo của chế độ phát triển (data-h2.sql); mật khẩu ghi trong README.
export const PASSWORD = 'MedBook@2026'

export const isoDay = (offsetDays) => {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export async function login(page, username) {
  await page.goto('/#/login')
  await page.locator('#login-identity').fill(username)
  await page.locator('#login-password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Đăng nhập' }).click()
  await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible()
}

/** Mở một phiên trình duyệt riêng (như một người dùng khác trên máy khác) và đăng nhập. */
export async function openSession(browser, username) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await login(page, username)
  return { context, page }
}

export const dialog = (page) => page.getByRole('dialog').last()

/** Token đang lưu ở trình duyệt, để gọi thẳng API với đúng phiên của người dùng trên giao diện. */
export const storedToken = (page) =>
  page.evaluate(() => {
    const raw = sessionStorage.getItem('medbook.auth') || localStorage.getItem('medbook.auth')
    return raw ? JSON.parse(raw).token : null
  })

/**
 * Bấm Tab cho tới khi tiêu điểm nằm ở phần tử cần tới. Dùng cho các ca "chỉ dùng bàn phím": nếu phần tử không
 * nhận được tiêu điểm bằng Tab thì ca kiểm thử thất bại.
 */
export async function tabTo(page, locator, maxPresses = 150) {
  await expect(locator).toBeVisible()
  for (let pressed = 0; pressed <= maxPresses; pressed += 1) {
    if (await locator.evaluate((element) => element === document.activeElement)) return
    await page.keyboard.press('Tab')
  }
  throw new Error(`Không tới được phần tử bằng phím Tab sau ${maxPresses} lần bấm`)
}
