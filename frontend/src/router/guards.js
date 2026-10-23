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
 * - `description: '...'`  → mô tả riêng của trang công khai; trang có mô tả riêng được lập chỉ mục.
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

/** Mô tả dùng cho trang không khai `meta.description` (trang sau đăng nhập, trang lỗi). */
export const DEFAULT_DESCRIPTION =
  'MedBook: tìm bác sĩ theo chuyên khoa, đặt lịch khám trực tuyến, theo dõi lịch hẹn, bệnh án, đơn thuốc và hóa đơn của bạn.'

function upsertHeadTag(doc, tagName, keyAttribute, key, valueAttribute, value) {
  let tag = doc.head.querySelector(`${tagName}[${keyAttribute}="${key}"]`)
  if (!tag) {
    tag = doc.createElement(tagName)
    tag.setAttribute(keyAttribute, key)
    doc.head.appendChild(tag)
  }
  tag.setAttribute(valueAttribute, value)
}

/**
 * Cập nhật các thẻ trong `<head>` theo trang vừa mở: tiêu đề, mô tả, thẻ Open Graph và địa chỉ chuẩn (tuyệt đối,
 * không kèm tham số). Chỉ trang công khai có `meta.description` riêng mới được lập chỉ mục; mọi trang khác mang
 * `noindex`.
 */
export function applyPageMeta(to, doc = document, origin = window.location.origin) {
  const matched = [...(to.matched ?? [])].reverse()
  const title = matched.find((record) => record.meta?.title)?.meta.title
  const ownDescription = matched.find((record) => record.meta?.description)?.meta.description
  const description = ownDescription || DEFAULT_DESCRIPTION
  const indexable = Boolean(ownDescription) && !routeAccess(to).requiresAuth

  doc.title = title ? `${title} · MedBook` : 'MedBook'
  upsertHeadTag(doc, 'meta', 'name', 'description', 'content', description)
  upsertHeadTag(doc, 'meta', 'property', 'og:title', 'content', doc.title)
  upsertHeadTag(doc, 'meta', 'property', 'og:description', 'content', description)
  upsertHeadTag(doc, 'link', 'rel', 'canonical', 'href', `${origin}${to.path}`)
  if (indexable) {
    doc.head.querySelector('meta[name="robots"]')?.remove()
  } else {
    upsertHeadTag(doc, 'meta', 'name', 'robots', 'content', 'noindex')
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

  router.afterEach((to) => applyPageMeta(to))

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
