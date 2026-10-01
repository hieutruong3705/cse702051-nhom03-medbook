import { computed, ref } from 'vue'
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
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function writeStorage(state) {
  try {
    if (state) localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    else localStorage.removeItem(STORAGE_KEY)
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
    writeStorage(token.value ? { token: token.value, user: user.value, expiresAt: expiresAt.value } : null)
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
      expireSession('expired')
      return
    }
    expiryTimer = setTimeout(() => expireSession('expired'), Math.min(remaining, MAX_TIMER_MS))
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
    clearTimer()
    token.value = null
    user.value = null
    expiresAt.value = null
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
      expireSession('expired')
      return false
    }
    return true
  }

  function login(payload) {
    if (!payload?.token) throw new Error('Phản hồi đăng nhập không có token')
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
    persist()
    scheduleExpiry()
  }

  /** Cập nhật hồ sơ hiển thị (ví dụ sau khi sửa họ tên) mà không đổi token. */
  function updateProfile(patch) {
    if (!user.value) return
    user.value = { ...user.value, ...patch, roles: user.value.roles, userId: user.value.userId }
    persist()
  }

  /**
   * Đăng xuất: xóa phiên ngay (UI không phải chờ mạng), sau đó báo server thu hồi token.
   * Lỗi khi báo server bị bỏ qua vì phiên phía client đã kết thúc.
   */
  async function logout() {
    const snapshot = token.value
    reset()
    if (!snapshot) return
    try {
      await http.post('/auth/logout', null, {
        headers: { Authorization: `Bearer ${snapshot}` },
        timeout: 5000,
        skipGlobalErrors: true
      })
    } catch {
      // đã đăng xuất phía client; token sẽ tự hết hạn phía server
    }
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
      runResetCallbacks()
      token.value = next.token
      user.value = next.user
      expiresAt.value = next.expiresAt
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
    onUnauthorized: () => expireSession('unauthorized')
  })

  // Token nạp từ localStorage có thể đã hết hạn; token còn hạn thì hẹn giờ tự đăng xuất.
  if (token.value) {
    if (!user.value) reset()
    else scheduleExpiry()
  }

  return {
    token,
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
