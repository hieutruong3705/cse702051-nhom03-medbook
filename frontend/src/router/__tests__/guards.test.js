import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { installAuthGuards, isSafeRedirect, resolvePostLoginTarget, routeAccess } from '@/router/guards'
import { createAppRouter } from '@/router'

const Dummy = { render: () => null }

function loginAs(auth, roles, extra = {}) {
  auth.login({
    token: 'h.p.s',
    userId: 1,
    username: 'u',
    fullName: 'Người dùng',
    roles,
    expiresAt: Date.now() + 3_600_000,
    ...extra
  })
}

function makeRouter() {
  const routes = [
    { path: '/', component: Dummy },
    { path: '/login', component: Dummy, meta: { guestOnly: true, title: 'Đăng nhập' } },
    { path: '/403', component: Dummy },
    { path: '/account', component: Dummy, meta: { requiresAuth: true } },
    {
      path: '/patient',
      component: Dummy,
      meta: { requiresAuth: true, roles: ['PATIENT'] },
      children: [
        { path: 'dashboard', component: Dummy, meta: { title: 'Tổng quan' } },
        { path: 'admin-only', component: Dummy, meta: { roles: ['ADMIN'] } }
      ]
    },
    { path: '/doctor', component: Dummy, meta: { roles: ['DOCTOR'] }, children: [{ path: 'dashboard', component: Dummy }] },
    { path: '/admin', component: Dummy, meta: { roles: ['ADMIN'] }, children: [{ path: 'dashboard', component: Dummy }] }
  ]
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuards(router, () => useAuthStore())
  return router
}

describe('isSafeRedirect', () => {
  it('chỉ chấp nhận đường dẫn nội bộ tuyệt đối', () => {
    expect(isSafeRedirect('/patient/dashboard')).toBe(true)
    expect(isSafeRedirect('/doctors?keyword=an&page=2')).toBe(true)
  })

  it.each([
    'https://evil.com',
    '//evil.com',
    '/\\evil.com',
    '/ok\\..\\evil',
    'javascript:alert(1)',
    'patient/dashboard',
    '',
    null,
    undefined,
    '/a\nb',
    `/${'a'.repeat(2100)}`
  ])('từ chối %j', (target) => {
    expect(isSafeRedirect(target)).toBe(false)
  })
})

describe('auth guard', () => {
  let router
  let auth

  beforeEach(() => {
    setActivePinia(createPinia())
    auth = useAuthStore()
    router = makeRouter()
  })

  it('khách vào trang cần đăng nhập → /login?redirect=', async () => {
    await router.push('/patient/dashboard')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/patient/dashboard')
  })

  it('trang công khai không yêu cầu đăng nhập', async () => {
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('đúng vai trò thì vào được, vai trò ở route cha được thừa hưởng', async () => {
    loginAs(auth, ['PATIENT'])
    await router.push('/patient/dashboard')
    expect(router.currentRoute.value.path).toBe('/patient/dashboard')
  })

  it.each([
    ['PATIENT', '/doctor/dashboard'],
    ['PATIENT', '/admin/dashboard'],
    ['DOCTOR', '/patient/dashboard'],
    ['DOCTOR', '/admin/dashboard'],
    ['ADMIN', '/patient/dashboard'],
    ['ADMIN', '/doctor/dashboard']
  ])('%s vào %s → /403', async (role, target) => {
    loginAs(auth, [role])
    await router.push(target)
    expect(router.currentRoute.value.path).toBe('/403')
  })

  it('route con ghi đè roles của route cha', async () => {
    loginAs(auth, ['PATIENT'])
    await router.push('/patient/admin-only')
    expect(router.currentRoute.value.path).toBe('/403')
  })

  it('route chỉ có requiresAuth mở cho mọi vai trò đã đăng nhập', async () => {
    loginAs(auth, ['DOCTOR'])
    await router.push('/account')
    expect(router.currentRoute.value.path).toBe('/account')
  })

  it('người đã đăng nhập vào /login được chuyển về dashboard theo vai trò', async () => {
    loginAs(auth, ['DOCTOR'])
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/doctor/dashboard')
  })

  it('phiên đã hết hạn bị coi như khách', async () => {
    loginAs(auth, ['PATIENT'], { expiresAt: Date.now() - 1000 })
    await router.push('/patient/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('hết phiên khi đang ở trang cần đăng nhập → về /login kèm reason và redirect', async () => {
    loginAs(auth, ['PATIENT'])
    await router.push('/patient/dashboard')

    auth.expireSession('expired')
    await router.isReady()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))

    expect(router.currentRoute.value.query).toMatchObject({ reason: 'expired', redirect: '/patient/dashboard' })
  })

  it('hết phiên khi đang ở trang công khai thì không bị đẩy đi', async () => {
    loginAs(auth, ['PATIENT'])
    await router.push('/')
    auth.expireSession('expired')
    await Promise.resolve()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('cập nhật document.title theo meta.title', async () => {
    await router.push('/login')
    expect(document.title).toBe('Đăng nhập · MedBook')
    await router.push('/')
    expect(document.title).toBe('MedBook')
  })

  it('403 khi tải dữ liệu (GET) chuyển tới /403 và phát sự kiện medbook:forbidden', async () => {
    loginAs(auth, ['PATIENT'])
    await router.push('/patient/dashboard')
    const listener = vi.fn()
    window.addEventListener('medbook:forbidden', listener)
    http.defaults.adapter = async (config) => {
      throw new AxiosError('x', AxiosError.ERR_BAD_REQUEST, config, null, {
        status: 403,
        data: { code: 'FORBIDDEN', message: 'Không có quyền' },
        headers: {},
        config
      })
    }

    await http.get('/medical-records/9').catch(() => {})
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/403'))

    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener('medbook:forbidden', listener)
  })

  it('403 của thao tác ghi không chuyển trang (để form tự hiển thị lỗi)', async () => {
    loginAs(auth, ['PATIENT'])
    await router.push('/patient/dashboard')
    http.defaults.adapter = async (config) => {
      throw new AxiosError('x', AxiosError.ERR_BAD_REQUEST, config, null, {
        status: 403,
        data: { code: 'FORBIDDEN', message: 'Không có quyền' },
        headers: {},
        config
      })
    }

    await http.post('/appointments', {}).catch(() => {})
    await Promise.resolve()

    expect(router.currentRoute.value.path).toBe('/patient/dashboard')
  })
})

describe('resolvePostLoginTarget', () => {
  let router
  let auth

  beforeEach(() => {
    setActivePinia(createPinia())
    auth = useAuthStore()
    router = makeRouter()
  })

  it('dùng redirect an toàn, tồn tại và đủ quyền', () => {
    loginAs(auth, ['PATIENT'])
    expect(resolvePostLoginTarget(router, auth, '/patient/dashboard')).toBe('/patient/dashboard')
    expect(resolvePostLoginTarget(router, auth, ['/account'])).toBe('/account')
  })

  it('về dashboard khi redirect không an toàn, không tồn tại hoặc không đủ quyền', () => {
    loginAs(auth, ['PATIENT'])
    const dashboard = '/patient/dashboard'
    expect(resolvePostLoginTarget(router, auth, 'https://evil.com')).toBe(dashboard)
    expect(resolvePostLoginTarget(router, auth, '//evil.com')).toBe(dashboard)
    expect(resolvePostLoginTarget(router, auth, '/khong-ton-tai')).toBe(dashboard)
    expect(resolvePostLoginTarget(router, auth, '/admin/dashboard')).toBe(dashboard)
    expect(resolvePostLoginTarget(router, auth, '/login')).toBe(dashboard)
    expect(resolvePostLoginTarget(router, auth, undefined)).toBe(dashboard)
  })
})

describe('routeAccess', () => {
  it('gom yêu cầu từ chuỗi route khớp', () => {
    const records = [{ meta: { requiresAuth: true, roles: ['PATIENT'] } }, { meta: { title: 'x' } }]
    expect(routeAccess({ matched: records })).toEqual({ roles: ['PATIENT'], requiresAuth: true, guestOnly: false })
    expect(routeAccess({ matched: [{ meta: { guestOnly: true } }] })).toEqual({
      roles: [],
      requiresAuth: false,
      guestOnly: true
    })
    expect(routeAccess(undefined)).toEqual({ roles: [], requiresAuth: false, guestOnly: false })
  })
})

describe('router gốc của ứng dụng', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('khu Admin/Bác sĩ/Bệnh nhân yêu cầu đúng vai trò; đường dẫn lạ → trang 404', async () => {
    const router = createAppRouter(createMemoryHistory())
    const auth = useAuthStore()

    await router.push('/admin/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')

    loginAs(auth, ['PATIENT'])
    await router.push('/admin/dashboard')
    expect(router.currentRoute.value.path).toBe('/403')
    expect(router.currentRoute.value.name).toBe('forbidden')

    await router.push('/khong-co-trang-nay')
    expect(router.currentRoute.value.name).toBe('not-found')
  })

  it('/patient chuyển về dashboard; ADMIN vào được /admin/dashboard', async () => {
    const router = createAppRouter(createMemoryHistory())
    const auth = useAuthStore()

    loginAs(auth, ['PATIENT'])
    await router.push('/patient')
    expect(router.currentRoute.value.path).toBe('/patient/dashboard')

    auth.login({ token: 'h.p.s', userId: 1, username: 'admin1', roles: ['ADMIN'], expiresAt: Date.now() + 3_600_000 })
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/admin/dashboard')
  })
})
