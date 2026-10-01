import { h } from 'vue'
import { createRouter, createWebHashHistory, RouterLink, RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { installAuthGuards } from './guards'

/**
 * Router gốc (Dev 5). Route con của từng khu vực nằm ở file `*.routes.js` cùng thư mục
 * và do chủ khu vực sở hữu:
 *   public.routes.js  → khu công khai + đăng nhập/đăng ký (Dev 5)
 *   patient.routes.js → khu Bệnh nhân            (Dev 5)
 *   doctor.routes.js  → khu Bác sĩ               (Dev 5)
 *   admin.routes.js   → khu Admin                (Dev 1)
 * Mỗi file `export default` một mảng route CON (đường dẫn tương đối, không có `/` đầu).
 *
 * Quyền truy cập khai báo bằng `meta` (xem guards.js): route cha của từng khu vực đã đặt
 * `roles`, route con chỉ cần thêm `meta.title` (hoặc `roles` riêng nếu muốn hẹp hơn).
 *
 * Layout (`src/layouts/*`) là component dạng slot do CHÍNH VIEW bọc (ví dụ
 * `<DashboardLayout>` trong view), nên route cha ở đây chỉ là khung trống `<RouterView />`.
 *
 * Hash history: Spring Boot phục vụ bản build tĩnh nên không cần cấu hình fallback SPA.
 */

const PassThrough = { name: 'PassThroughLayout', render: () => h(RouterView) }

const pages = import.meta.glob('../views/*.vue')
const routeModules = import.meta.glob('./*.routes.js', { eager: true })

const routesOf = (name) => routeModules[`./${name}.routes.js`]?.default ?? []

function fallbackErrorPage(title, message) {
  return {
    name: `Fallback${title.replace(/\W/g, '')}`,
    render: () =>
      h('section', { class: 'mx-auto max-w-xl px-4 py-16 text-center' }, [
        h('h1', { class: 'text-3xl font-bold text-gray-900' }, title),
        h('p', { class: 'mt-3 text-gray-600' }, message),
        h(RouterLink, { to: '/', class: 'mt-6 inline-block font-semibold text-cyan-700 underline' }, () => 'Về trang chủ')
      ])
  }
}

const forbiddenPage =
  pages['../views/403.vue'] ?? fallbackErrorPage('403 — Không có quyền truy cập', 'Bạn không được phép xem trang này.')
const notFoundPage =
  pages['../views/404.vue'] ?? fallbackErrorPage('404 — Không tìm thấy trang', 'Đường dẫn không tồn tại hoặc đã bị di chuyển.')

const routes = [
  { path: '/', component: PassThrough, children: routesOf('public') },
  {
    path: '/patient',
    component: PassThrough,
    meta: { requiresAuth: true, roles: ['PATIENT'] },
    children: [{ path: '', redirect: '/patient/dashboard' }, ...routesOf('patient')]
  },
  {
    path: '/doctor',
    component: PassThrough,
    meta: { requiresAuth: true, roles: ['DOCTOR'] },
    children: [{ path: '', redirect: '/doctor/dashboard' }, ...routesOf('doctor')]
  },
  {
    path: '/admin',
    component: PassThrough,
    meta: { requiresAuth: true, roles: ['ADMIN'] },
    children: [{ path: '', redirect: '/admin/dashboard' }, ...routesOf('admin')]
  },
  { path: '/403', name: 'forbidden', component: forbiddenPage, meta: { title: 'Không có quyền truy cập' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: notFoundPage, meta: { title: 'Không tìm thấy trang' } }
]

export function createAppRouter(history = createWebHashHistory()) {
  const router = createRouter({
    history,
    routes,
    scrollBehavior: (to, from, savedPosition) => savedPosition ?? { top: 0 }
  })
  installAuthGuards(router, () => useAuthStore())
  return router
}

export default createAppRouter()
