import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * Lớp tương thích mỏng bọc store `auth` (Pinia) cho các view/layout cũ dùng `useAuth()`.
 * Mã mới nên dùng thẳng `useAuthStore()` từ '@/stores/auth'.
 *
 * - `login(payload)` nhận phản hồi `POST /auth/login`; vẫn chấp nhận chữ ký cũ `login(token, user)`.
 * - `user.id` là bí danh của `userId` để các view cũ không vỡ; dùng `patientId`/`doctorId` khi
 *   cần ID hồ sơ bệnh nhân/bác sĩ.
 */
export function useAuth() {
  const auth = useAuthStore()

  const login = (first, second) => {
    if (typeof first === 'string') {
      auth.login({ ...(second || {}), token: first })
    } else {
      auth.login(first)
    }
  }

  return {
    isAuthenticated: computed(() => auth.isAuthenticated),
    user: computed(() => (auth.user ? { ...auth.user, id: auth.user.userId } : {})),
    roles: computed(() => auth.roles),
    dashboardRoute: computed(() => auth.dashboardPath),
    hasRole: auth.hasRole,
    login,
    logout: () => auth.logout()
  }
}
