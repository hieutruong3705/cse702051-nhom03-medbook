<template>
  <div
    v-if="auth.isAuthenticated"
    class="flex items-center gap-2"
    :class="stacked ? 'flex-col items-stretch' : 'flex-wrap'"
    data-test="user-menu-authenticated"
  >
    <span class="text-sm" :class="stacked ? 'px-2' : 'hidden text-right sm:block'">
      <span class="block text-slate-500">{{ roleLabel }}</span>
      <span class="font-semibold text-slate-900">{{ auth.displayName }}</span>
    </span>

    <RouterLink
      v-if="variant === 'public'"
      :to="auth.dashboardPath"
      class="rounded-md px-3 py-2 text-sm font-semibold text-primary-700 hover:bg-primary-50"
      @click="$emit('navigate')"
    >
      Bảng điều khiển
    </RouterLink>
    <RouterLink
      to="/account/profile"
      class="rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
      @click="$emit('navigate')"
    >
      <i class="fa-regular fa-user mr-1" aria-hidden="true"></i>Hồ sơ
    </RouterLink>
    <RouterLink
      to="/account/change-password"
      class="rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
      @click="$emit('navigate')"
    >
      <i class="fa-solid fa-key mr-1" aria-hidden="true"></i>Đổi mật khẩu
    </RouterLink>
    <BaseButton variant="secondary" icon="fa-solid fa-right-from-bracket" data-test="logout" @click="logout">
      Đăng xuất
    </BaseButton>
  </div>

  <div v-else class="flex items-center gap-2" :class="stacked ? 'flex-col items-stretch' : ''" data-test="user-menu-guest">
    <RouterLink
      to="/login"
      class="rounded-md px-3 py-2 text-center text-sm font-semibold text-slate-700 hover:bg-slate-100"
      @click="$emit('navigate')"
    >
      Đăng nhập
    </RouterLink>
    <RouterLink
      to="/register"
      class="rounded-md bg-primary-600 px-4 py-2 text-center text-sm font-semibold text-white hover:bg-primary-700"
      @click="$emit('navigate')"
    >
      Đăng ký
    </RouterLink>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  /** `public`: có thêm liên kết "Bảng điều khiển"; `dashboard`: dùng trong header của khu làm việc. */
  variant: { type: String, default: 'public' },
  /** Xếp dọc (menu di động). */
  stacked: { type: Boolean, default: false }
})

const emit = defineEmits(['navigate'])

const router = useRouter()
const auth = useAuthStore()

const ROLE_LABELS = { ADMIN: 'Quản trị viên', DOCTOR: 'Bác sĩ', PATIENT: 'Bệnh nhân' }
const roleLabel = computed(() => ROLE_LABELS[auth.primaryRole] || 'Tài khoản')

// Xóa phiên phía client NGAY (không chờ mạng); việc báo server thu hồi token chạy nền.
const logout = () => {
  auth.logout()
  emit('navigate')
  router.replace({ path: '/login', query: { reason: 'logout' } })
}
</script>
