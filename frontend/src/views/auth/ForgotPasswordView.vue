<template>
  <PublicLayout>
    <section class="mx-auto flex min-h-[calc(100vh-8rem)] max-w-md items-center px-4 py-12">
      <form class="w-full rounded-md border border-slate-200 bg-white p-6 shadow-sm" novalidate @submit.prevent="submit">
        <h1 class="text-2xl font-bold text-slate-950">Quên mật khẩu</h1>
        <p class="mt-1 text-sm text-slate-600">Nhập email đã đăng ký, hệ thống sẽ gửi hướng dẫn đặt lại mật khẩu.</p>
        <div class="mt-6 space-y-4">
          <BaseInput id="forgot-email" v-model="values.email" label="Email" type="email" required autocomplete="email" :error="errors.email" />
          <p v-if="message" class="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">{{ message }}</p>
          <p v-if="formError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>
          <BaseButton class="w-full" type="submit" :loading="submitting" icon="fa-solid fa-paper-plane">Gửi hướng dẫn</BaseButton>
          <RouterLink class="block text-center text-sm font-semibold text-primary-700 hover:text-primary-900" to="/login">Quay lại đăng nhập</RouterLink>
        </div>
      </form>
    </section>
  </PublicLayout>
</template>

<script setup>
import { ref } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { authApi } from '@/api/auth'
import { useForm } from '@/composables/useForm'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { validateEmail } from '@/utils/authValidation'

const message = ref('')
const formError = ref('')
const { values, errors, submitting, validate } = useForm({ email: '' }, { email: validateEmail })

const submit = async () => {
  if (submitting.value) return
  message.value = ''
  formError.value = ''
  if (!validate()) return
  submitting.value = true
  try {
    await authApi.forgotPassword({ email: values.email.trim() })
    // Server luôn phản hồi như nhau dù email có tồn tại hay không (không lộ thông tin tài khoản).
    message.value = 'Nếu email đã được đăng ký, hướng dẫn đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra hộp thư.'
  } catch (err) {
    formError.value = apiErrorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>
