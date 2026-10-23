<template>
  <DoctorLayout>
    <PageHeader title="Hồ sơ bác sĩ" eyebrow="Bác sĩ" description="Thông tin hồ sơ công khai và tài khoản đang sử dụng." />

    <section class="grid gap-4 lg:grid-cols-2">
      <article class="rounded-md border border-slate-200 bg-white p-5">
        <h2 class="font-semibold text-slate-950">Thông tin tài khoản</h2>
        <dl class="mt-4 space-y-3 text-sm">
          <div><dt class="text-slate-500">Họ tên</dt><dd class="font-medium">{{ user.fullName || '-' }}</dd></div>
          <div><dt class="text-slate-500">Email</dt><dd>{{ user.email || '-' }}</dd></div>
          <div><dt class="text-slate-500">Điện thoại</dt><dd>{{ user.phone || '-' }}</dd></div>
        </dl>
        <RouterLink class="mt-4 inline-block text-sm font-semibold text-primary-700 hover:underline" to="/account/profile">Sửa tài khoản</RouterLink>
      </article>

      <article class="rounded-md border border-slate-200 bg-white p-5">
        <h2 class="font-semibold text-slate-950">Hồ sơ chuyên môn</h2>
        <p v-if="error" class="mt-3 text-sm text-red-700">{{ error }}</p>
        <dl v-else class="mt-4 space-y-3 text-sm">
          <div><dt class="text-slate-500">Chuyên khoa</dt><dd class="font-medium">{{ doctor.specialty?.name || doctor.specialtyName || '-' }}</dd></div>
          <div><dt class="text-slate-500">Số giấy phép</dt><dd>{{ doctor.licenseNumber || '-' }}</dd></div>
          <div><dt class="text-slate-500">Giới thiệu</dt><dd>{{ doctor.bio || '-' }}</dd></div>
        </dl>
      </article>
    </section>
  </DoctorLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { PageHeader } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { doctorsApi } from '@/api/doctors'
import { usersApi } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const auth = useAuthStore()
const user = ref({})
const doctor = ref({})
const error = ref('')

onMounted(async () => {
  try {
    user.value = await usersApi.me()
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
  if (!auth.user?.doctorId) return
  try {
    doctor.value = await doctorsApi.get(auth.user.doctorId)
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
})
</script>
