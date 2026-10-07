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
  await page.goto(route)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('h1').first()).toBeVisible()
  // đợi hiệu ứng chuyển trang chạy xong, nếu không màu chữ đang mờ dần sẽ bị tính là thiếu tương phản
  await page.evaluate(() => Promise.all(document.getAnimations().map((animation) => animation.finished.catch(() => null))))
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
