import { configureHttp } from '@/api/http'

/**
 * Guard điều hướng dùng chung cho toàn bộ router.
 *
 * Quy ước `meta` trên route (route con thừa hưởng từ route cha, route sâu hơn ghi đè):
 * - `requiresAuth: true`  → phải đăng nhập.
 * - `roles: ['DOCTOR']`   → phải có ít nhất một vai trò trong danh sách (kéo theo requiresAuth).
 * - `guestOnly: true`     → chỉ dành cho khách (đăng nhập/đăng ký...); người đã đăng nhập
 *                           được chuyển về dashboard theo vai trò.
 * - `title: '...'`        → tiêu đề trang (document.title).
 */

/** Chỉ chấp nhận đường dẫn nội bộ tuyệt đối; chặn open-redirect (`//evil.com`, `https://...`, `/\`). */
export function isSafeRedirect(target) {
  if (typeof target !== 'string' || target.length === 0 || target.length > 2000) return false
  if (!target.startsWith('/')) return false
  if (target.startsWith('//') || target.includes('\\')) return false
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(target)) return false
  return true
}

/** Gom yêu cầu truy cập từ chuỗi route khớp (`route.matched`). */
export function routeAccess(route) {
  const matched = route?.matched ?? []
  let roles = []
  for (const record of matched) {
    if (record.meta?.roles?.length) roles = record.meta.roles
  }
  return {
    roles,
    requiresAuth: roles.length > 0 || matched.some((record) => record.meta?.requiresAuth),
    guestOnly: matched.some((record) => record.meta?.guestOnly)
  }
}

/**
 * Đích sau khi đăng nhập thành công: `redirect` từ query nếu an toàn, tồn tại, người dùng
 * đủ quyền và không phải trang chỉ-dành-cho-khách; ngược lại là dashboard theo vai trò.
 */
export function resolvePostLoginTarget(router, auth, redirect) {
  const fallback = auth.dashboardPath
  const candidate = Array.isArray(redirect) ? redirect[0] : redirect
  if (!isSafeRedirect(candidate)) return fallback

  const resolved = router.resolve(candidate)
  if (!resolved.matched.length) return fallback

  const { roles, guestOnly } = routeAccess(resolved)
  if (guestOnly) return fallback
  if (roles.length && !auth.hasRole(...roles)) return fallback
  return resolved.fullPath
}

/**
 * @param {() => ReturnType<typeof import('@/stores/auth').useAuthStore>} getAuth
 */
export function createAuthGuard(getAuth) {
  return (to) => {
    const auth = getAuth()
    const { roles, requiresAuth, guestOnly } = routeAccess(to)
    const authenticated = auth.validateSession()

    if (guestOnly && authenticated) {
      return { path: auth.dashboardPath, replace: true }
    }
    if (!requiresAuth) return true

    if (!authenticated) {
      return {
        path: '/login',
        query: isSafeRedirect(to.fullPath) ? { redirect: to.fullPath } : {},
        replace: true
      }
    }
    if (roles.length && !auth.hasRole(...roles)) {
      return { path: '/403', replace: true }
    }
    return true
  }
}

const wiredStores = new WeakSet()

/**
 * Gắn guard vào router và nối các sự kiện phiên:
 * - hết phiên/401/đăng xuất ở tab khác → về /login (kèm `reason`, `redirect` nếu đang ở trang cần đăng nhập);
 * - 403 khi tải dữ liệu (GET) → chuyển tới /403; thao tác ghi để nơi gọi tự hiển thị lỗi.
 *   Luôn phát sự kiện DOM `medbook:forbidden` để ToastHost hiển thị thông báo.
 * @param {import('vue-router').Router} router
 * @param {() => ReturnType<typeof import('@/stores/auth').useAuthStore>} getAuth
 */
export function installAuthGuards(router, getAuth) {
  const guard = createAuthGuard(getAuth)

  router.beforeEach((to) => {
    const auth = getAuth()
    if (!wiredStores.has(auth)) {
      wiredStores.add(auth)
      auth.setSessionEndedHandler((reason) => {
        const current = router.currentRoute.value
        if (!routeAccess(current).requiresAuth) return
        const query = { reason }
        if (isSafeRedirect(current.fullPath)) query.redirect = current.fullPath
        router.replace({ path: '/login', query })
      })
    }
    return guard(to)
  })

  router.afterEach((to) => {
    const title = [...to.matched].reverse().find((record) => record.meta?.title)?.meta.title
    document.title = title ? `${title} · MedBook` : 'MedBook'
  })

  configureHttp({
    onForbidden: (error, config) => {
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('medbook:forbidden', { detail: error }))
      }
      const isRead = (config?.method || 'get').toLowerCase() === 'get'
      if (isRead && router.currentRoute.value.path !== '/403') {
        router.replace('/403')
      }
    }
  })
}
