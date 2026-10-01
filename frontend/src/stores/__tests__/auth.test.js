import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { http } from '@/api/http'
import { dashboardPathFor, decodeJwtPayload, normalizeRole, onSessionReset, useAuthStore } from '@/stores/auth'

const b64url = (obj) => {
  const bytes = new TextEncoder().encode(JSON.stringify(obj))
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const fakeJwt = (payload) => `${b64url({ alg: 'HS256' })}.${b64url(payload)}.signature`

const loginPayload = (overrides = {}) => ({
  token: fakeJwt({ sub: 'patient1', exp: Math.floor(Date.now() / 1000) + 3600 }),
  tokenType: 'Bearer',
  userId: 4,
  username: 'patient1',
  email: 'patient1@medbook.local',
  fullName: 'Nguyễn Minh An',
  roles: ['PATIENT'],
  patientId: 1,
  doctorId: null,
  ...overrides
})

function useAdapter(handler) {
  http.defaults.adapter = async (config) => {
    const result = await handler(config)
    const response = { data: result?.data ?? null, status: result?.status ?? 200, statusText: '', headers: {}, config }
    if (response.status >= 400) {
      throw new AxiosError('failed', AxiosError.ERR_BAD_REQUEST, config, null, response)
    }
    return response
  }
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useAdapter(() => ({ status: 204 }))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('login lưu danh tính đã chuẩn hóa và persist vào localStorage', () => {
    const auth = useAuthStore()

    auth.login(loginPayload({ roles: ['ROLE_PATIENT'] }))

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user).toEqual({
      userId: 4,
      username: 'patient1',
      fullName: 'Nguyễn Minh An',
      email: 'patient1@medbook.local',
      roles: ['PATIENT'],
      patientId: 1,
      doctorId: null
    })
    expect(auth.displayName).toBe('Nguyễn Minh An')
    const saved = JSON.parse(localStorage.getItem('medbook.auth'))
    expect(saved.token).toBe(auth.token)
    expect(saved.user.roles).toEqual(['PATIENT'])
  })

  it('không lưu mật khẩu hay trường lạ từ phản hồi đăng nhập', () => {
    const auth = useAuthStore()

    auth.login(loginPayload({ password: 'x', passwordHash: 'y', message: 'ok' }))

    const saved = JSON.stringify(JSON.parse(localStorage.getItem('medbook.auth')))
    expect(saved).not.toContain('passwordHash')
    expect(saved).not.toContain('"password"')
    expect(auth.user.message).toBeUndefined()
  })

  it('hạn phiên lấy từ expiresAt nếu có, nếu không thì từ claim exp của JWT', () => {
    const auth = useAuthStore()
    const future = new Date(Date.now() + 120_000).toISOString()

    auth.login(loginPayload({ expiresAt: future }))
    expect(auth.expiresAt).toBe(Date.parse(future))

    const exp = Math.floor(Date.now() / 1000) + 600
    auth.login(loginPayload({ token: fakeJwt({ exp }) }))
    expect(auth.expiresAt).toBe(exp * 1000)
  })

  it('hasRole nhận nhiều vai trò và chấp nhận tiền tố ROLE_', () => {
    const auth = useAuthStore()
    auth.login(loginPayload({ roles: ['DOCTOR'], doctorId: 2, patientId: null }))

    expect(auth.hasRole('DOCTOR')).toBe(true)
    expect(auth.hasRole('ROLE_DOCTOR')).toBe(true)
    expect(auth.hasRole('ADMIN', 'DOCTOR')).toBe(true)
    expect(auth.hasRole(['ADMIN', 'PATIENT'])).toBe(false)
  })

  it('dashboardPath theo vai trò ưu tiên ADMIN > DOCTOR > PATIENT', () => {
    expect(dashboardPathFor(['PATIENT'])).toBe('/patient/dashboard')
    expect(dashboardPathFor(['PATIENT', 'DOCTOR'])).toBe('/doctor/dashboard')
    expect(dashboardPathFor(['ROLE_ADMIN', 'DOCTOR'])).toBe('/admin/dashboard')
    expect(dashboardPathFor([])).toBe('/')
    expect(normalizeRole(' role_admin ')).toBe('ADMIN')
  })

  it('logout xóa phiên ngay lập tức rồi báo server bằng token cũ', async () => {
    const calls = []
    useAdapter((config) => {
      calls.push({ url: config.url, method: config.method, auth: config.headers.Authorization })
      return { status: 204 }
    })
    const auth = useAuthStore()
    auth.login(loginPayload())
    const oldToken = auth.token

    await auth.logout()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('medbook.auth')).toBeNull()
    expect(calls).toEqual([{ url: '/auth/logout', method: 'post', auth: `Bearer ${oldToken}` }])
  })

  it('logout vẫn thành công phía client khi server lỗi/không kết nối', async () => {
    http.defaults.adapter = async (config) => {
      throw new AxiosError('Network Error', AxiosError.ERR_NETWORK, config)
    }
    const auth = useAuthStore()
    auth.login(loginPayload())

    await expect(auth.logout()).resolves.toBeUndefined()

    expect(auth.isAuthenticated).toBe(false)
  })

  it('đổi tài khoản và đăng xuất đều chạy callback dọn dữ liệu của các store khác', async () => {
    const cleanup = vi.fn()
    const off = onSessionReset(cleanup)
    const auth = useAuthStore()

    auth.login(loginPayload()) // nạp phiên đầu: dọn dữ liệu cũ
    expect(cleanup).toHaveBeenCalledTimes(1)

    auth.login(loginPayload({ userId: 2, username: 'doctor1', roles: ['DOCTOR'] })) // đổi tài khoản
    expect(cleanup).toHaveBeenCalledTimes(2)
    expect(auth.user.username).toBe('doctor1')

    await auth.logout()
    expect(cleanup).toHaveBeenCalledTimes(3)
    off()
  })

  it('callback dọn dữ liệu bị lỗi không chặn các callback còn lại', () => {
    const second = vi.fn()
    const offA = onSessionReset(() => {
      throw new Error('store hỏng')
    })
    const offB = onSessionReset(second)
    const auth = useAuthStore()
    auth.login(loginPayload())

    auth.reset()

    expect(second).toHaveBeenCalled()
    offA()
    offB()
  })

  it('phiên đã hết hạn trong localStorage bị loại bỏ ngay khi nạp store', () => {
    localStorage.setItem(
      'medbook.auth',
      JSON.stringify({
        token: 'a.b.c',
        user: { userId: 4, username: 'patient1', roles: ['PATIENT'] },
        expiresAt: Date.now() - 1000
      })
    )

    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.validateSession()).toBe(false)
    expect(localStorage.getItem('medbook.auth')).toBeNull()
  })

  it('validateSession phát hiện hết hạn dù bộ hẹn giờ chưa kịp chạy (máy ngủ, đổi giờ)', () => {
    vi.useFakeTimers()
    const auth = useAuthStore()
    const ended = vi.fn()
    auth.setSessionEndedHandler(ended)
    auth.login(loginPayload({ expiresAt: Date.now() + 60_000 }))

    vi.setSystemTime(Date.now() + 120_000) // nhảy giờ mà không chạy timer

    expect(auth.validateSession()).toBe(false)
    expect(auth.isAuthenticated).toBe(false)
    expect(ended).toHaveBeenCalledWith('expired')
  })

  it('tự kết thúc phiên đúng lúc hết hạn và báo handler', () => {
    vi.useFakeTimers()
    const auth = useAuthStore()
    const ended = vi.fn()
    auth.setSessionEndedHandler(ended)

    auth.login(loginPayload({ expiresAt: Date.now() + 60_000 }))
    expect(auth.isAuthenticated).toBe(true)

    vi.advanceTimersByTime(59_000)
    expect(auth.isAuthenticated).toBe(true)

    vi.advanceTimersByTime(2_000)
    expect(auth.isAuthenticated).toBe(false)
    expect(ended).toHaveBeenCalledWith('expired')
  })

  it('nhận 401 từ API → phiên bị kết thúc với lý do unauthorized', async () => {
    useAdapter(() => ({ status: 401, data: { code: 'UNAUTHORIZED', message: 'Hết phiên' } }))
    const auth = useAuthStore()
    const ended = vi.fn()
    auth.setSessionEndedHandler(ended)
    auth.login(loginPayload())

    await expect(http.get('/patients/me')).rejects.toMatchObject({ status: 401 })

    expect(auth.isAuthenticated).toBe(false)
    expect(ended).toHaveBeenCalledWith('unauthorized')
  })

  it('đăng xuất ở tab khác (storage event) kết thúc phiên ở tab này', () => {
    const auth = useAuthStore()
    const ended = vi.fn()
    auth.setSessionEndedHandler(ended)
    auth.login(loginPayload())

    localStorage.removeItem('medbook.auth')
    window.dispatchEvent(new StorageEvent('storage', { key: 'medbook.auth' }))

    expect(auth.isAuthenticated).toBe(false)
    expect(ended).toHaveBeenCalledWith('logged-out-elsewhere')
  })

  it('đăng nhập tài khoản khác ở tab khác được nạp vào tab này và dọn dữ liệu cũ', () => {
    const cleanup = vi.fn()
    const off = onSessionReset(cleanup)
    const auth = useAuthStore()
    auth.login(loginPayload())
    cleanup.mockClear()

    localStorage.setItem(
      'medbook.auth',
      JSON.stringify({
        token: fakeJwt({ exp: Math.floor(Date.now() / 1000) + 3600, sub: 'admin1' }),
        user: { userId: 1, username: 'admin1', roles: ['ADMIN'] },
        expiresAt: Date.now() + 3_600_000
      })
    )
    window.dispatchEvent(new StorageEvent('storage', { key: 'medbook.auth' }))

    expect(auth.user.username).toBe('admin1')
    expect(auth.hasRole('ADMIN')).toBe(true)
    expect(cleanup).toHaveBeenCalledTimes(1)
    off()
  })

  it('xóa các khóa token/user cũ của phiên bản trước khi đăng nhập', () => {
    localStorage.setItem('token', 'old')
    localStorage.setItem('user', '{}')
    const auth = useAuthStore()

    auth.login(loginPayload())

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
  })

  it('login từ chối phản hồi không có token', () => {
    const auth = useAuthStore()

    expect(() => auth.login({ userId: 1 })).toThrow()
    expect(auth.isAuthenticated).toBe(false)
  })

  it('decodeJwtPayload đọc được payload UTF-8 và trả null với chuỗi rác', () => {
    expect(decodeJwtPayload(fakeJwt({ sub: 'Nguyễn', exp: 5 }))).toEqual({ sub: 'Nguyễn', exp: 5 })
    expect(decodeJwtPayload('rac')).toBeNull()
    expect(decodeJwtPayload(null)).toBeNull()
  })
})
