import { computed, ref, onScopeDispose } from 'vue'
import { defineStore, getActivePinia } from 'pinia'
import { configureHttp, http } from '@/api/http'

/**
 * Phiên đăng nhập của người dùng.
 *
 * - `login(payload)` nhận đúng phản hồi của `POST /auth/login`
 *   ({ token, expiresAt?, userId, username, email, fullName, roles[], patientId, doctorId }).
 * - Khóa lưu trữ `medbook.auth` (localStorage). Token sống trong localStorage nên KHÔNG lưu
 *   dữ liệu y tế nhạy cảm ở đây; chỉ danh tính tối thiểu.
 * - Khi đăng xuất / đổi tài khoản / hết phiên: xóa phiên và chạy mọi callback đăng ký bằng
 *   {@link onSessionReset} để các store khác xóa dữ liệu (không lộ dữ liệu của vai trò trước).
 */

const STORAGE_KEY = 'medbook.auth'
const LEGACY_KEYS = ['token', 'user']
const ROLE_PRIORITY = ['ADMIN', 'DOCTOR', 'PATIENT']
const DASHBOARDS = {
  ADMIN: '/admin/dashboard',
  DOCTOR: '/doctor/dashboard',
  PATIENT: '/patient/dashboard'
}
const MAX_TIMER_MS = 2 ** 31 - 1

const resetCallbacks = new Set()

/**
 * Đăng ký hàm dọn dữ liệu khi phiên bị xóa. Trả về hàm hủy đăng ký.
 * Dùng cho store kiểu "setup" (không có `$reset`).
 */
export function onSessionReset(callback) {
  resetCallbacks.add(callback)
  return () => resetCallbacks.delete(callback)
}

export function normalizeRole(role) {
  const value = String(role ?? '').trim().toUpperCase()
  return value.startsWith('ROLE_') ? value.slice(5) : value
}

export function dashboardPathFor(roles = []) {
  const normalized = roles.map(normalizeRole)
  const primary = ROLE_PRIORITY.find((role) => normalized.includes(role))
  return primary ? DASHBOARDS[primary] : '/'
}

/** Giải mã payload JWT (KHÔNG xác minh chữ ký — việc đó do server làm). */
export function decodeJwtPayload(token) {
  try {
    const part = String(token).split('.')[1]
    if (!part) return null
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

function toEpochMs(value) {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') return value
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? null : parsed
}

function computeExpiry(payload) {
  const explicit = toEpochMs(payload.expiresAt)
  if (explicit) return explicit
  const exp = decodeJwtPayload(payload.token)?.exp
  return typeof exp === 'number' ? exp * 1000 : null
}

function readStorage() {
  try {
    const tab = sessionStorage.getItem(STORAGE_KEY)
    if (tab) return JSON.parse(tab)
    const raw = localStorage.getItem(STORAGE_KEY)
    const state = raw ? JSON.parse(raw) : null
    // Do not copy a shared refresh credential into several independent tabs.
    if (state?.refreshToken && !globalThis.navigator?.locks?.request) return null
    return state
  } catch {
    return null
  }
}

function writeStorage(state) {
  try {
    if (state) {
      // Without Web Locks, each tab owns its refresh session to avoid replay across tabs.
      const tabOnly = state.refreshToken && !globalThis.navigator?.locks?.request
      const target = tabOnly ? sessionStorage : localStorage
      const other = tabOnly ? localStorage : sessionStorage
      target.setItem(STORAGE_KEY, JSON.stringify(state))
      other.removeItem(STORAGE_KEY)
    } else {
      localStorage.removeItem(STORAGE_KEY)
      sessionStorage.removeItem(STORAGE_KEY)
    }
    LEGACY_KEYS.forEach((key) => localStorage.removeItem(key))
  } catch {
    // Trình duyệt chặn lưu trữ: phiên chỉ tồn tại trong bộ nhớ.
  }
}

let storageListener = null

export const useAuthStore = defineStore('auth', () => {
  const stored = readStorage()
  const token = ref(stored?.token || null)
  const user = ref(stored?.user || null)
  const expiresAt = ref(stored?.expiresAt || null)
  const refreshToken = ref(stored?.refreshToken || null)
  const refreshExpiresAt = ref(stored?.refreshExpiresAt || null)
  let refreshPromise = null
  let generation = 0

  let expiryTimer = null
  let sessionEndedHandler = null

  const roles = computed(() => user.value?.roles || [])
  const isAuthenticated = computed(() => Boolean(token.value && user.value))
  const primaryRole = computed(() => ROLE_PRIORITY.find((role) => roles.value.includes(role)) || null)
  const dashboardPath = computed(() => dashboardPathFor(roles.value))
  const displayName = computed(() => user.value?.fullName || user.value?.username || '')

  function hasRole(...wanted) {
    const mine = roles.value
    return wanted.flat().some((role) => mine.includes(normalizeRole(role)))
  }

  function persist() {
    writeStorage(token.value ? { token: token.value, user: user.value, expiresAt: expiresAt.value, refreshToken: refreshToken.value, refreshExpiresAt: refreshExpiresAt.value } : null)
  }

  function clearTimer() {
    if (expiryTimer) {
      clearTimeout(expiryTimer)
      expiryTimer = null
    }
  }

  function scheduleExpiry() {
    clearTimer()
    if (!token.value || !expiresAt.value) return
    const remaining = expiresAt.value - Date.now()
    if (remaining <= 0) {
      if (canRefresh()) void refreshSession().catch(() => {})
      else expireSession('expired')
      return
    }
    expiryTimer = setTimeout(() => {
      if (canRefresh()) void refreshSession().catch(() => {})
      else expireSession('expired')
    }, Math.min(canRefresh() ? Math.max(1000, remaining - 30_000) : remaining, MAX_TIMER_MS))
  }

  function runResetCallbacks() {
    resetCallbacks.forEach((callback) => {
      try {
        callback()
      } catch {
        // một store lỗi không được chặn việc dọn các store còn lại
      }
    })
    // Store kiểu "options" có sẵn $reset
    getActivePinia()?._s?.forEach((store) => {
      if (store.$id !== 'auth' && typeof store.$reset === 'function') {
        try {
          store.$reset()
        } catch {
          // store kiểu setup không hỗ trợ $reset
        }
      }
    })
  }

  /** Xóa toàn bộ phiên và dữ liệu liên quan (không gọi API). */
  function reset() {
    generation++
    refreshPromise = null
    clearTimer()
    token.value = null
    user.value = null
    expiresAt.value = null
    refreshToken.value = null
    refreshExpiresAt.value = null
    persist()
    runResetCallbacks()
  }

  /** Phiên kết thúc ngoài ý muốn (hết hạn, 401, đăng xuất ở tab khác). */
  function expireSession(reason = 'expired') {
    if (!token.value && !user.value) return
    reset()
    sessionEndedHandler?.(reason)
  }

  /** Kiểm tra token còn hạn; nếu đã hết hạn thì kết thúc phiên. */
  function validateSession() {
    if (!token.value || !user.value) return false
    if (expiresAt.value && expiresAt.value <= Date.now()) {
      if (canRefresh()) { void refreshSession().catch(() => {}); return true }
      expireSession('expired')
      return false
    }
    return true
  }

  function login(payload) {
    if (!payload?.token) throw new Error('Phản hồi đăng nhập không có token')
    generation++
    clearTimer()
    refreshPromise = null
    // Đổi tài khoản: xóa mọi dữ liệu của phiên trước trước khi nạp phiên mới.
    runResetCallbacks()
    token.value = payload.token
    user.value = {
      userId: payload.userId ?? null,
      username: payload.username ?? '',
      fullName: payload.fullName ?? '',
      email: payload.email ?? '',
      roles: (payload.roles || []).map(normalizeRole),
      patientId: payload.patientId ?? null,
      doctorId: payload.doctorId ?? null
    }
    expiresAt.value = computeExpiry(payload)
    refreshToken.value = payload.refreshToken || null
    refreshExpiresAt.value = toEpochMs(payload.refreshExpiresAt)
    persist()
    scheduleExpiry()
  }

  /** Cập nhật hồ sơ hiển thị (ví dụ sau khi sửa họ tên) mà không đổi token. */
  function updateProfile(patch) {
    if (!user.value) return
    user.value = { ...user.value, ...patch, roles: user.value.roles, userId: user.value.userId }
    persist()
  }

  function canRefresh() {
    return Boolean(refreshToken.value && refreshExpiresAt.value > Date.now())
  }

  function sameSession(a, b) {
    const first = decodeJwtPayload(a)?.sid
    return Boolean(first && first === decodeJwtPayload(b)?.sid)
  }

  async function refreshSession(failedToken = null) {
    if (failedToken && failedToken !== token.value) {
      return sameSession(failedToken, token.value) ? token.value : null
    }
    if (refreshPromise) return refreshPromise
    if (!canRefresh()) {
      expireSession('unauthorized')
      return null
    }
    const version = generation
    const before = token.value
    const execute = async () => {
      if (generation !== version) return null
      if (globalThis.navigator?.locks?.request) {
        const latest = readStorage()
        if (!latest?.token) { expireSession('logged-out-elsewhere'); return null }
        if (latest.token !== before) {
          if (!sameSession(before, latest.token)) return null
          token.value = latest.token
          expiresAt.value = latest.expiresAt
          refreshToken.value = latest.refreshToken
          refreshExpiresAt.value = latest.refreshExpiresAt
          scheduleExpiry()
          return token.value
        }
      }
      try {
        const response = await http.post('/auth/refresh', { refreshToken: refreshToken.value }, { skipGlobalErrors: true })
        if (generation !== version) return null
        const next = response.data
        if (!next?.token || !next.refreshToken || !toEpochMs(next.expiresAt) || !toEpochMs(next.refreshExpiresAt)) {
          throw new Error('Phản hồi làm mới phiên không hợp lệ')
        }
        token.value = next.token
        refreshToken.value = next.refreshToken
        expiresAt.value = toEpochMs(next.expiresAt)
        refreshExpiresAt.value = toEpochMs(next.refreshExpiresAt)
        persist()
        scheduleExpiry()
        return token.value
      } catch (error) {
        // No retry of a rotation whose result may have been lost in transit.
        if (generation === version) expireSession(error.status === 401 ? 'unauthorized' : 'refresh-failed')
        throw error
      }
    }
    const task = globalThis.navigator?.locks?.request
      ? navigator.locks.request('medbook.auth.refresh', execute)
      : execute()
    refreshPromise = task
    try { return await task } finally { if (refreshPromise === task) refreshPromise = null }
  }

  /** Confirm server revocation before clearing a refresh-enabled session. */
  async function logout() {
    if (!token.value) return
    if (!refreshToken.value) {
      // Compatibility for legacy sessions without refresh credentials.
      const snapshot = token.value
      reset()
      try { await http.post('/auth/logout', null, { headers: { Authorization: `Bearer ${snapshot}` }, skipGlobalErrors: true, timeout: 5000 }) } catch {}
      return
    }
    if (refreshPromise) await refreshPromise
    if (expiresAt.value <= Date.now()) await refreshSession()
    if (!token.value) return
    const snapshot = token.value
    try {
      await http.post('/auth/logout', null, { headers: { Authorization: `Bearer ${snapshot}` }, skipGlobalErrors: true, timeout: 5000 })
    } catch (error) {
      if (error.status !== 401) throw error
    }
    if (token.value === snapshot) reset()
  }

  function setSessionEndedHandler(handler) {
    sessionEndedHandler = handler
  }

  function syncFromStorage() {
    const next = readStorage()
    if (!next?.token) {
      if (token.value) expireSession('logged-out-elsewhere')
      return
    }
    if (next.token !== token.value) {
      if (!sameSession(token.value, next.token)) { generation++; runResetCallbacks() }
      token.value = next.token
      user.value = next.user
      expiresAt.value = next.expiresAt
      refreshToken.value = next.refreshToken || null
      refreshExpiresAt.value = next.refreshExpiresAt || null
      scheduleExpiry()
    }
  }

  // Đồng bộ đăng nhập/đăng xuất giữa các tab.
  if (typeof window !== 'undefined') {
    if (storageListener) window.removeEventListener('storage', storageListener)
    storageListener = (event) => {
      if (event.key === STORAGE_KEY || event.key === null) syncFromStorage()
    }
    window.addEventListener('storage', storageListener)
  }

  configureHttp({
    getToken: () => token.value,
    refreshToken: refreshSession,
    onUnauthorized: (_error, config) => {
      const sent = config?.headers?.Authorization?.replace(/^Bearer /, '')
      if (!sent || sent === token.value) expireSession('unauthorized')
    }
  })

  // Token nạp từ localStorage có thể đã hết hạn; token còn hạn thì hẹn giờ tự đăng xuất.
  if (token.value) {
    if (!user.value) reset()
    else scheduleExpiry()
  }

  onScopeDispose(clearTimer)

  return {
    token,
    refreshToken,
    refreshExpiresAt,
    refreshSession,
    user,
    expiresAt,
    roles,
    isAuthenticated,
    primaryRole,
    dashboardPath,
    displayName,
    hasRole,
    login,
    logout,
    reset,
    expireSession,
    validateSession,
    updateProfile,
    setSessionEndedHandler
  }
})
