<template>
  <PublicLayout>
    <section class="mx-auto max-w-2xl px-4 py-12">
      <form class="rounded-md border border-slate-200 bg-white p-6 shadow-sm" novalidate @submit.prevent="submit">
        <h1 class="text-2xl font-bold text-slate-950">Tạo tài khoản bệnh nhân</h1>
        <p class="mt-1 text-sm text-slate-600">Tài khoản mới mặc định có vai trò bệnh nhân (PATIENT).</p>

        <div class="mt-6 grid gap-4 sm:grid-cols-2">
          <BaseInput id="register-full-name" v-model="values.fullName" label="Họ và tên" required autocomplete="name" :error="errors.fullName" />
          <BaseInput id="register-phone" v-model="values.phone" label="Số điện thoại" required autocomplete="tel" :error="errors.phone" />
          <BaseInput id="register-email" v-model="values.email" label="Email" type="email" required autocomplete="email" :error="errors.email" />
          <BaseInput id="register-username" v-model="values.username" label="Tên đăng nhập" required autocomplete="username" :error="errors.username" />
          <BaseInput
            id="register-password"
            v-model="values.password"
            label="Mật khẩu"
            type="password"
            required
            autocomplete="new-password"
            hint="Từ 8 đến 100 ký tự, gồm chữ cái và chữ số."
            :error="errors.password"
          />
          <BaseInput
            id="register-confirm-password"
            v-model="values.confirmPassword"
            label="Nhập lại mật khẩu"
            type="password"
            required
            autocomplete="new-password"
            :error="errors.confirmPassword"
          />
          <BaseDatePicker id="register-dob" v-model="values.dateOfBirth" label="Ngày sinh" :error="errors.dateOfBirth" />
        </div>

        <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>

        <div class="mt-6 flex flex-wrap justify-end gap-3">
          <RouterLink class="rounded-md px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100" :to="loginTarget">Đã có tài khoản</RouterLink>
          <BaseButton type="submit" :loading="submitting" icon="fa-solid fa-user-plus">Đăng ký</BaseButton>
        </div>
      </form>
    </section>
  </PublicLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseDatePicker from '@/components/ui/BaseDatePicker.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { authApi } from '@/api/auth'
import { useForm } from '@/composables/useForm'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import {
  required,
  validateBirthDate,
  validateConfirm,
  validateEmail,
  validatePassword,
  validatePhone,
  validateUsername
} from '@/utils/authValidation'

const router = useRouter()
const formError = ref('')

const { values, errors, submitting, validate, applyApiErrors } = useForm(
  { username: '', password: '', confirmPassword: '', fullName: '', email: '', phone: '', dateOfBirth: '' },
  {
    fullName: required('Họ và tên'),
    phone: validatePhone,
    email: validateEmail,
    username: validateUsername,
    password: validatePassword,
    confirmPassword: validateConfirm('password'),
    dateOfBirth: validateBirthDate
  }
)

const loginTarget = computed(() => ({
  path: '/login',
  query: values.username.trim() ? { username: values.username.trim() } : {}
}))

const submit = async () => {
  if (submitting.value) return
  formError.value = ''
  if (!validate()) {
    formError.value = 'Vui lòng kiểm tra lại các trường được đánh dấu.'
    return
  }
  submitting.value = true
  try {
    const payload = {
      username: values.username.trim(),
      password: values.password,
      fullName: values.fullName.trim(),
      email: values.email.trim(),
      phone: values.phone.trim()
    }
    if (values.dateOfBirth) payload.dateOfBirth = values.dateOfBirth
    await authApi.register(payload)
    router.replace({ path: '/login', query: { reason: 'registered', username: payload.username } })
  } catch (err) {
    applyApiErrors(err)
    formError.value = Object.keys(err?.details || {}).length
      ? 'Vui lòng kiểm tra lại các trường được đánh dấu.'
      : apiErrorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>
