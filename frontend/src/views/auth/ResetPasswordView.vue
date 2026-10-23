<template>
  <PublicLayout>
    <section class="mx-auto flex min-h-[calc(100vh-8rem)] max-w-md items-center px-4 py-12">
      <form class="w-full rounded-md border border-slate-200 bg-white p-6 shadow-sm" novalidate @submit.prevent="submit">
        <h1 class="text-2xl font-bold text-slate-950">Đặt lại mật khẩu</h1>
        <p class="mt-1 text-sm text-slate-600">Nhập mã trong email và mật khẩu mới của bạn.</p>
        <div class="mt-6 space-y-4">
          <BaseInput id="reset-token" v-model="values.token" label="Mã đặt lại" required :error="errors.token" />
          <BaseInput
            id="reset-password"
            v-model="values.newPassword"
            label="Mật khẩu mới"
            type="password"
            required
            autocomplete="new-password"
            hint="Từ 8 đến 100 ký tự, gồm chữ cái và chữ số."
            :error="errors.newPassword"
          />
          <BaseInput
            id="reset-confirm-password"
            v-model="values.confirmPassword"
            label="Nhập lại mật khẩu mới"
            type="password"
            required
            autocomplete="new-password"
            :error="errors.confirmPassword"
          />
          <p v-if="message" class="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">
            {{ message }}
            <RouterLink class="ml-1 font-semibold underline" to="/login">Đăng nhập</RouterLink>
          </p>
          <p v-if="formError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>
          <BaseButton class="w-full" type="submit" :loading="submitting" icon="fa-solid fa-key">Đặt lại mật khẩu</BaseButton>
          <RouterLink class="block text-center text-sm font-semibold text-primary-700 hover:text-primary-900" to="/forgot-password">Gửi lại mã mới</RouterLink>
        </div>
      </form>
    </section>
  </PublicLayout>
</template>

<script setup>
import { ref, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { authApi } from '@/api/auth'
import { useForm } from '@/composables/useForm'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { required, validateConfirm, validatePassword } from '@/utils/authValidation'

const route = useRoute()
const message = ref('')
const formError = ref('')

const { values, errors, submitting, validate, applyApiErrors } = useForm(
  { token: '', newPassword: '', confirmPassword: '' },
  {
    token: required('Mã đặt lại'),
    newPassword: validatePassword,
    confirmPassword: validateConfirm('newPassword')
  }
)

// Liên kết trong email có dạng /reset-password?token=...; điền sẵn để người dùng chỉ cần nhập mật khẩu mới.
watchEffect(() => {
  const token = Array.isArray(route.query.token) ? route.query.token[0] : route.query.token
  if (typeof token === 'string' && token) values.token = token
})

const submit = async () => {
  if (submitting.value) return
  message.value = ''
  formError.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    await authApi.resetPassword({ token: values.token.trim(), newPassword: values.newPassword })
    values.newPassword = ''
    values.confirmPassword = ''
    message.value = 'Mật khẩu đã được đặt lại. Các phiên đăng nhập cũ đã bị vô hiệu.'
  } catch (err) {
    applyApiErrors(err)
    formError.value = apiErrorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>
