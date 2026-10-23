<template>
  <DashboardLayout title="Bảo mật tài khoản" :items="[]">
    <section class="mx-auto max-w-xl px-4 py-8">
      <form class="rounded-md border border-slate-200 bg-white p-6 shadow-sm" novalidate @submit.prevent="submit">
        <p class="text-sm font-semibold uppercase tracking-wide text-primary-700">Tài khoản</p>
        <h1 class="mt-2 text-2xl font-bold text-slate-950">Đổi mật khẩu</h1>
        <p class="mt-1 text-sm text-slate-600">
          Nhập mật khẩu hiện tại và mật khẩu mới. Sau khi đổi, mọi phiên đăng nhập (kể cả phiên này) sẽ bị đăng xuất.
        </p>

        <div class="mt-6 space-y-4">
          <BaseInput id="change-old-password" v-model="values.oldPassword" label="Mật khẩu hiện tại" type="password" required autocomplete="current-password" :error="errors.oldPassword" />
          <BaseInput
            id="change-new-password"
            v-model="values.newPassword"
            label="Mật khẩu mới"
            type="password"
            required
            autocomplete="new-password"
            hint="Từ 8 đến 100 ký tự, gồm chữ cái và chữ số."
            :error="errors.newPassword"
          />
          <BaseInput
            id="change-confirm-password"
            v-model="values.confirmPassword"
            label="Nhập lại mật khẩu mới"
            type="password"
            required
            autocomplete="new-password"
            :error="errors.confirmPassword"
          />
        </div>

        <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>

        <div class="mt-6 flex flex-wrap justify-between gap-3">
          <RouterLink class="rounded-md px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100" to="/account/profile">Về hồ sơ</RouterLink>
          <BaseButton type="submit" :loading="submitting" icon="fa-solid fa-key">Đổi mật khẩu</BaseButton>
        </div>
      </form>
    </section>
  </DashboardLayout>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import DashboardLayout from '@/layouts/DashboardLayout.vue'
import { authApi } from '@/api/auth'
import { useForm } from '@/composables/useForm'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { required, validateConfirm, validatePassword } from '@/utils/authValidation'

const router = useRouter()
const auth = useAuthStore()
const formError = ref('')

const { values, errors, submitting, validate, applyApiErrors } = useForm(
  { oldPassword: '', newPassword: '', confirmPassword: '' },
  {
    oldPassword: required('Mật khẩu hiện tại'),
    newPassword: (value, form) => {
      const invalid = validatePassword(value)
      if (invalid) return invalid
      return value === form.oldPassword ? 'Mật khẩu mới phải khác mật khẩu hiện tại' : ''
    },
    confirmPassword: validateConfirm('newPassword')
  }
)

const submit = async () => {
  if (submitting.value) return
  formError.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    await authApi.changePassword({ oldPassword: values.oldPassword, newPassword: values.newPassword })
    // Server đã vô hiệu mọi phiên cũ (kể cả phiên này): xóa phiên phía client và yêu cầu đăng nhập lại.
    auth.reset()
    router.replace({ path: '/login', query: { reason: 'password-changed' } })
  } catch (err) {
    applyApiErrors(err)
    formError.value = apiErrorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>
