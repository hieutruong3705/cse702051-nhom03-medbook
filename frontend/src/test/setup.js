import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, vi } from 'vitest'

// Layout của bệnh nhân và bác sĩ có chuông thông báo tự hỏi số chưa đọc khi hiển thị. Mặc định mọi test dùng bản
// giả dưới đây để không test nào gọi mạng thật; test về thông báo tự khai báo vi.mock riêng để kiểm tra chi tiết.
vi.mock('@/api/notifications', () => ({
  notificationsApi: {
    mine: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    unreadCount: vi.fn().mockResolvedValue({ count: 0 }),
    markRead: vi.fn().mockResolvedValue({}),
    markAllRead: vi.fn().mockResolvedValue({})
  }
}))

config.global.stubs = {
  RouterLink: {
    props: ['to'],
    template: '<a :href="typeof to === `string` ? to : `#`"><slot /></a>'
  }
}

// jsdom chưa cài scrollTo; router gọi nó khi điều hướng (scrollBehavior).
window.scrollTo = () => {}

// Mỗi test bắt đầu với localStorage sạch và một Pinia mới để phiên đăng nhập không rò rỉ giữa
// các test; layout/composable dùng store `auth` không cần tự dựng Pinia.
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  setActivePinia(createPinia())
})

afterEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
