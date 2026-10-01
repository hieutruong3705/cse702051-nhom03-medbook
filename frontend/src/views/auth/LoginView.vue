<template>
  <PublicLayout>
    <section class="mx-auto grid min-h-[calc(100vh-8rem)] max-w-6xl items-center gap-10 px-4 py-12 lg:grid-cols-[1fr_420px]">
      <div class="hidden lg:block">
        <p class="text-sm font-semibold uppercase tracking-wide text-primary-700">MedBook</p>
        <h1 class="mt-3 text-4xl font-bold text-slate-950">Đăng nhập để tiếp tục đặt khám và quản lý hồ sơ.</h1>
        <p class="mt-4 max-w-xl text-slate-600">
          Một tài khoản dùng cho bệnh nhân, bác sĩ và quản trị. Quyền truy cập được xác định sau khi đăng nhập.
        </p>
      </div>

      <form class="rounded-md border border-slate-200 bg-white p-6 shadow-sm" novalidate @submit.prevent="submit">
        <h2 class="text-2xl font-bold text-slate-950">Đăng nhập</h2>
        <p class="mt-1 text-sm text-slate-600">Nhập tên đăng nhập hoặc email của bạn.</p>

        <div class="mt-6 space-y-4">
          <p v-if="sessionMessage.text" :class="sessionMessage.success ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'" class="rounded-md px-3 py-2 text-sm" role="status">
            {{ sessionMessage.text }}
          </p>
          <BaseInput id="login-identity" v-model="form.usernameOrEmail" label="Tên đăng nhập hoặc email" required autocomplete="username" />
          <BaseInput id="login-password" v-model="form.password" label="Mật khẩu" type="password" required autocomplete="current-password" />
          <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
          <BaseButton class="w-full" type="submit" :loading="loading" icon="fa-solid fa-right-to-bracket">Đăng nhập</BaseButton>
        </div>

        <div class="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
          <RouterLink class="font-medium text-primary-700 hover:text-primary-900" to="/forgot-password">Quên mật khẩu?</RouterLink>
          <RouterLink class="font-medium text-primary-700 hover:text-primary-900" to="/register">Tạo tài khoản</RouterLink>
        </div>
      </form>
    </section>
  </PublicLayout>
</template>

<script setup>
import { computed, reactive, ref, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { authApi } from '@/api/auth'
import { resolvePostLoginTarget } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const form = reactive({ usernameOrEmail: '', password: '' })
const loading = ref(false)
const error = ref('')

watchEffect(() => {
  const username = Array.isArray(route.query.username) ? route.query.username[0] : route.query.username
  if (username && !form.usernameOrEmail) form.usernameOrEmail = username
})

const SESSION_MESSAGES = {
  expired: { text: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' },
  unauthorized: { text: 'Bạn cần đăng nhập để tiếp tục.' },
  'logged-out-elsewhere': { text: 'Tài khoản đã đăng xuất ở tab khác.' },
  logout: { text: 'Bạn đã đăng xuất.', success: true },
  registered: { text: 'Đăng ký thành công. Hãy đăng nhập để tiếp tục.', success: true },
  'password-changed': { text: 'Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.', success: true }
}

const sessionMessage = computed(() => {
  const reason = Array.isArray(route.query.reason) ? route.query.reason[0] : route.query.reason
  return SESSION_MESSAGES[reason] || { text: '' }
})

const submit = async () => {
  if (loading.value) return
  if (!form.usernameOrEmail.trim() || !form.password) {
    error.value = 'Vui lòng nhập đủ tên đăng nhập/email và mật khẩu.'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const session = await authApi.login({ ...form, usernameOrEmail: form.usernameOrEmail.trim() })
    auth.login(session)
    router.replace(resolvePostLoginTarget(router, auth, route.query.redirect))
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}
</script>
