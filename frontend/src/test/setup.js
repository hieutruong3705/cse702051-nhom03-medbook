import { config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach } from 'vitest'

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
