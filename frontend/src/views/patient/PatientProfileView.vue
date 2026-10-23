<template>
  <PatientLayout>
    <PageHeader title="Hồ sơ bệnh nhân" eyebrow="Bệnh nhân" description="Cập nhật thông tin hành chính và thông tin sức khỏe cơ bản của bạn." />

    <form class="max-w-3xl rounded-md border border-slate-200 bg-white p-5" @submit.prevent="submit">
      <div class="grid gap-4 md:grid-cols-2">
        <BaseInput id="patient-full-name" v-model="form.fullName" label="Họ tên" required />
        <BaseDatePicker id="patient-dob" v-model="form.dateOfBirth" label="Ngày sinh" />
        <BaseSelect id="patient-gender" v-model="form.genderCode" label="Giới tính" :options="genderOptions" />
        <BaseInput id="patient-phone" v-model="form.phone" label="Số điện thoại" />
        <BaseInput id="patient-email" v-model="form.email" label="Email" type="email" />
        <BaseInput id="patient-blood-type" v-model="form.bloodType" label="Nhóm máu" />
        <div class="md:col-span-2">
          <BaseTextarea id="patient-address" v-model="form.address" label="Địa chỉ" rows="2" />
        </div>
        <div class="md:col-span-2">
          <BaseTextarea id="patient-allergies" v-model="form.allergies" label="Dị ứng" rows="3" />
        </div>
      </div>

      <p v-if="error" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error }}</p>
      <p v-if="successMessage" class="mt-4 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{{ successMessage }}</p>

      <div class="mt-5 flex justify-end">
        <BaseButton type="submit" :loading="saving">
          <i class="fa-solid fa-floppy-disk" aria-hidden="true"></i>
          Lưu hồ sơ
        </BaseButton>
      </div>
    </form>
  </PatientLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, BaseTextarea, PageHeader } from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { patientsApi } from '@/api/patients'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const genderOptions = [
  { label: 'Chưa chọn', value: '' },
  { label: 'Nam', value: 'MALE' },
  { label: 'Nữ', value: 'FEMALE' },
  { label: 'Khác', value: 'OTHER' }
]

const form = reactive({
  fullName: '',
  dateOfBirth: '',
  genderCode: '',
  phone: '',
  email: '',
  address: '',
  bloodType: '',
  allergies: ''
})
const saving = ref(false)
const error = ref('')
const successMessage = ref('')

onMounted(async () => {
  try {
    const profile = await patientsApi.me()
    Object.assign(form, {
      fullName: profile.fullName || profile.user?.fullName || '',
      dateOfBirth: profile.dateOfBirth || '',
      genderCode: profile.genderCode || '',
      phone: profile.phone || profile.user?.phone || '',
      email: profile.email || profile.user?.email || '',
      address: profile.address || '',
      bloodType: profile.bloodType || '',
      allergies: profile.allergies || ''
    })
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
})

async function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  successMessage.value = ''
  try {
    await patientsApi.updateMe({ ...form })
    successMessage.value = 'Đã lưu hồ sơ.'
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    saving.value = false
  }
}
</script>
