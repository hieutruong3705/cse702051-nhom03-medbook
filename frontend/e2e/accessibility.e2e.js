import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { login } from './support'

// Kiểm tra tự động WCAG 2.1 mức A và AA bằng axe-core trên ứng dụng thật (gồm cả tỷ lệ tương phản màu).
// Công cụ tự động chỉ bắt được một phần lỗi; phần còn lại đối chiếu tay ở docs/wcag-checklist.md.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

const PAGES = {
  'khách': ['/#/', '/#/login', '/#/register', '/#/forgot-password', '/#/doctors', '/#/specialties', '/#/services'],
  patient1: [
    '/#/patient/dashboard',
    '/#/patient/booking',
    '/#/patient/appointments',
    '/#/patient/records',
    '/#/patient/invoices',
    '/#/patient/notifications',
    '/#/patient/profile',
    '/#/account/change-password'
  ],
  doctor1: [
    '/#/doctor/dashboard',
    '/#/doctor/schedules',
    '/#/doctor/appointments',
    '/#/doctor/patients',
    '/#/doctor/notifications',
    '/#/doctor/profile'
  ],
  admin1: [
    '/#/admin/dashboard',
    '/#/admin/users',
    '/#/admin/doctors',
    '/#/admin/catalog',
    '/#/admin/appointments',
    '/#/admin/invoices',
    '/#/admin/reports',
    '/#/admin/audit-logs'
  ]
}

async function violationsOf(page, route) {
  // Đổi trang trong ứng dụng một trang chạy hiệu ứng "mờ ra rồi mờ vào" và chỉ bắt đầu sau khi tải xong mã của trang
  // mới. Ghi nhớ khung nhìn đang hiện rồi đợi tới khi nó được thay bằng khung nhìn mới, nếu không axe sẽ quét trang cũ
  // hoặc quét đúng lúc trang mới còn đang mờ.
  const alreadyThere = await page.evaluate((target) => location.hash === target.slice(1), route)
  if (!alreadyThere) {
    await page.evaluate(() => {
      window.__previousView = document.querySelector('#main-content > *')
    })
  }
  await page.goto(route)
  if (!alreadyThere) {
    await page
      .waitForFunction(() => {
        const view = document.querySelector('#main-content > *')
        return view && view !== window.__previousView
      }, null, { timeout: 5_000 })
      .catch(() => null) // hai đường dẫn dùng chung một khung nhìn thì không có gì để đợi
  }
  await page.waitForLoadState('networkidle')
  await expect(page.locator('h1').first()).toBeVisible()
  // Đợi hiệu ứng chuyển trang chạy xong, nếu không màu chữ đang mờ dần sẽ bị tính là thiếu tương phản. Hiệu ứng có
  // thể bắt đầu trễ vài khung hình sau khi dữ liệu về, nên chỉ coi là xong khi 10 khung hình liền không còn gì chạy.
  await page.evaluate(async () => {
    const nextFrame = () => new Promise((resolve) => requestAnimationFrame(resolve))
    for (let quietFrames = 0; quietFrames < 10; ) {
      const running = document.getAnimations().filter((animation) =>
        animation.playState !== 'finished' && animation.playState !== 'idle' &&
        animation.effect?.getComputedTiming().iterations !== Infinity)
      if (running.length) {
        await Promise.all(running.map((animation) => animation.finished.catch(() => null)))
        quietFrames = 0
      } else {
        quietFrames += 1
      }
      await nextFrame()
    }
  })
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  return violations.flatMap((violation) =>
    violation.nodes.map((node) => `${route} · ${violation.id} (${violation.impact}) · ${node.target.join(' ')} · ${node.failureSummary?.split('\n')[1]?.trim() ?? ''}`)
  )
}

for (const [who, routes] of Object.entries(PAGES)) {
  test(`${who}: các trang không vi phạm WCAG 2.1 A/AA theo axe`, async ({ page }) => {
    test.setTimeout(120_000)
    if (who !== 'khách') await login(page, who)
    const found = []
    for (const route of routes) found.push(...(await violationsOf(page, route)))
    expect(found, found.join('\n')).toEqual([])
  })
}
