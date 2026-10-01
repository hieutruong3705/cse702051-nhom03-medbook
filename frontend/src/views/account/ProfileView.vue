<template>
  <DashboardLayout title="Hồ sơ cá nhân" :items="[]">
    <form class="mx-auto max-w-2xl rounded-md border border-slate-200 bg-white p-6 shadow-sm" @submit.prevent="submit">
      <h1 class="text-2xl font-bold text-slate-950">Thông tin tài khoản</h1>
      <div class="mt-6 grid gap-4 sm:grid-cols-2">
        <BaseInput id="profile-full-name" v-model="form.fullName" label="Họ tên" required />
        <BaseInput id="profile-phone" v-model="form.phone" label="Số điện thoại" />
        <BaseInput id="profile-email" v-model="form.email" label="Email" type="email" required />
      </div>
      <p v-if="error" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error }}</p>
      <p v-if="success" class="mt-4 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{{ success }}</p>
      <div class="mt-6 flex justify-end">
        <BaseButton type="submit" :loading="loading" icon="fa-solid fa-floppy-disk">Lưu thay đổi</BaseButton>
      </div>
    </form>
  </DashboardLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import DashboardLayout from '@/layouts/DashboardLayout.vue'
import { usersApi } from '@/api/users'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const form = reactive({ fullName: '', phone: '', email: '' })
const loading = ref(false)
const error = ref('')
const success = ref('')

onMounted(async () => {
  try {
    const profile = await usersApi.me()
    Object.assign(form, {
      fullName: profile.fullName || '',
      phone: profile.phone || '',
      email: profile.email || ''
    })
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
})

const submit = async () => {
  if (loading.value) return
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    await usersApi.updateMe({ ...form })
    success.value = 'Đã lưu hồ sơ.'
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}
</script>
