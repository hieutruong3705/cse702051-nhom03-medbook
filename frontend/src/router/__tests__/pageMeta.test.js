import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { applyPageMeta, DEFAULT_DESCRIPTION } from '@/router/guards'
import { createAppRouter, legacyHashTarget } from '@/router'

const PUBLIC_PAGES = ['/', '/doctors', '/specialties', '/services', '/login', '/register']

const metaContent = (selector) => document.head.querySelector(selector)?.getAttribute('content') ?? null
const description = () => metaContent('meta[name="description"]')
const robots = () => metaContent('meta[name="robots"]')
const canonical = () => document.head.querySelector('link[rel="canonical"]')?.getAttribute('href') ?? null

function loginAs(roles) {
  useAuthStore().login({
    token: 'h.p.s',
    userId: 1,
    username: 'u',
    fullName: 'Người dùng',
    roles,
    expiresAt: Date.now() + 3_600_000
  })
}

describe('thẻ mô tả theo trang', () => {
  it('mỗi trang công khai có tiêu đề và mô tả riêng, không trùng nhau và khác mô tả chung', async () => {
    const router = createAppRouter(createMemoryHistory())
    const titles = new Set()
    const descriptions = new Set()

    for (const path of PUBLIC_PAGES) {
      await router.push(path)
      expect(document.title).toMatch(/ · MedBook$/)
      expect(description().length).toBeGreaterThan(40)
      expect(description()).not.toBe(DEFAULT_DESCRIPTION)
      titles.add(document.title)
      descriptions.add(description())
    }

    expect(titles.size).toBe(PUBLIC_PAGES.length)
    expect(descriptions.size).toBe(PUBLIC_PAGES.length)
  })

  it('mô tả đúng nội dung trang: trang bác sĩ nói về bác sĩ, trang dịch vụ nói về dịch vụ', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/doctors')
    expect(document.title).toBe('Bác sĩ · MedBook')
    expect(description()).toContain('bác sĩ')

    await router.push('/services')
    expect(document.title).toBe('Dịch vụ · MedBook')
    expect(description()).toContain('dịch vụ')
  })

  it('thẻ Open Graph đi theo tiêu đề và mô tả của trang đang mở', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/specialties')
    expect(metaContent('meta[property="og:title"]')).toBe('Chuyên khoa · MedBook')
    expect(metaContent('meta[property="og:description"]')).toBe(description())
    expect(document.head.querySelectorAll('meta[property="og:title"]')).toHaveLength(1)
    expect(document.head.querySelectorAll('meta[name="description"]')).toHaveLength(1)
  })

  it('địa chỉ chuẩn là địa chỉ tuyệt đối của trang, không kèm tham số tìm kiếm', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/doctors?specialtyId=1&page=2')
    expect(canonical()).toBe(`${window.location.origin}/doctors`)
    expect(canonical()).toMatch(/^https?:\/\//)

    await router.push('/')
    expect(canonical()).toBe(`${window.location.origin}/`)
    expect(document.head.querySelectorAll('link[rel="canonical"]')).toHaveLength(1)
  })

  it('trang công khai được lập chỉ mục; trang sau đăng nhập dùng mô tả chung và mang noindex', async () => {
    const router = createAppRouter(createMemoryHistory())

    await router.push('/doctors')
    expect(robots()).toBeNull()

    loginAs(['PATIENT'])
    await router.push('/patient/dashboard')
    expect(router.currentRoute.value.path).toBe('/patient/dashboard')
    expect(description()).toBe(DEFAULT_DESCRIPTION)
    expect(robots()).toBe('noindex')

    await router.push('/services')
    expect(robots()).toBeNull()
  })

  it('trang lỗi và trang đặt lại mật khẩu không được lập chỉ mục', async () => {
    const router = createAppRouter(createMemoryHistory())

    for (const path of ['/khong-co-trang-nay', '/403', '/reset-password?token=abc', '/forgot-password']) {
      await router.push(path)
      expect(robots(), path).toBe('noindex')
      expect(description(), path).toBe(DEFAULT_DESCRIPTION)
    }
  })

  it('applyPageMeta ghi vào tài liệu và địa chỉ gốc được truyền vào', () => {
    const doc = document.implementation.createHTMLDocument('')
    applyPageMeta(
      { path: '/services', matched: [{ meta: {} }, { meta: { title: 'Dịch vụ', description: 'Bảng dịch vụ khám.' } }] },
      doc,
      'https://medbook.example'
    )

    expect(doc.title).toBe('Dịch vụ · MedBook')
    expect(doc.head.querySelector('meta[name="description"]').getAttribute('content')).toBe('Bảng dịch vụ khám.')
    expect(doc.head.querySelector('link[rel="canonical"]').getAttribute('href')).toBe('https://medbook.example/services')
    expect(doc.head.querySelector('meta[name="robots"]')).toBeNull()
  })
})

describe('liên kết cũ có dấu #', () => {
  afterEach(() => {
    window.history.replaceState(null, '', '/')
    vi.resetModules()
  })

  it('đổi /#/doctors?specialtyId=1 sang /doctors?specialtyId=1, giữ nguyên tham số', () => {
    expect(legacyHashTarget({ pathname: '/', hash: '#/doctors?specialtyId=1' })).toBe('/doctors?specialtyId=1')
    expect(legacyHashTarget({ pathname: '/index.html', hash: '#/login' })).toBe('/login')
    expect(legacyHashTarget({ pathname: '/', hash: '#/' })).toBe('/')
  })

  it('không đụng tới neo trong trang, địa chỉ mới và đích không an toàn', () => {
    expect(legacyHashTarget({ pathname: '/', hash: '#main-content' })).toBeNull()
    expect(legacyHashTarget({ pathname: '/', hash: '' })).toBeNull()
    expect(legacyHashTarget({ pathname: '/doctors', hash: '#/login' })).toBeNull()
    expect(legacyHashTarget({ pathname: '/', hash: '#//evil.example' })).toBeNull()
    expect(legacyHashTarget({ pathname: '/', hash: '#/\\evil.example' })).toBeNull()
    expect(legacyHashTarget(undefined)).toBeNull()
  })

  it('ứng dụng khởi động từ một liên kết cũ thì thanh địa chỉ được đổi sang địa chỉ mới', async () => {
    window.history.replaceState(null, '', '/#/doctors?specialtyId=1')
    vi.resetModules()

    const { default: router } = await import('@/router')

    expect(window.location.pathname).toBe('/doctors')
    expect(window.location.search).toBe('?specialtyId=1')
    expect(window.location.hash).toBe('')
    expect(router.resolve(window.location.pathname + window.location.search).name).toBe('doctors')
  })

  it('liên kết đặt lại mật khẩu dạng mới mở đúng trang và mang theo mã', () => {
    const router = createAppRouter(createMemoryHistory())
    const resolved = router.resolve('/reset-password?token=ma-dat-lai')

    expect(resolved.name).toBe('reset-password')
    expect(resolved.query.token).toBe('ma-dat-lai')
  })
})
