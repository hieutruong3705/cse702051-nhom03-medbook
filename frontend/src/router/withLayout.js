import { defineAsyncComponent, h } from 'vue'

/**
 * Bọc một view "trần" (chưa tự dùng layout) vào layout dạng slot của `src/layouts/*` ngay ở
 * cấp route, để trang cũ vẫn có thanh điều hướng/sidebar mà không phải sửa view.
 *
 * View mới nên TỰ bọc layout trong template (như `views/auth/LoginView.vue`) và khai báo
 * route trực tiếp bằng `() => import(...)`; đừng dùng helper này cho các view đã tự bọc vì
 * sẽ bị lồng hai lớp layout.
 *
 * @param {() => Promise<{default: object}>} loadLayout ví dụ `() => import('@/layouts/PatientLayout.vue')`
 * @param {() => Promise<{default: object}>} loadView   ví dụ `() => import('@/views/PatientDashboard.vue')`
 */
export function withLayout(loadLayout, loadView) {
  return defineAsyncComponent(async () => {
    const [layout, view] = await Promise.all([loadLayout(), loadView()])
    return {
      name: 'RouteWithLayout',
      render: () => h(layout.default, null, { default: () => h(view.default) })
    }
  })
}
