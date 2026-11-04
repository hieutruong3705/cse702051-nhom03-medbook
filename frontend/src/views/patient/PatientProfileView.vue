<template>
  <PatientLayout>
    <PageHeader title="Hồ sơ bệnh nhân" eyebrow="Bệnh nhân" description="Cập nhật thông tin hành chính, thông tin sức khỏe và người liên hệ khẩn cấp của bạn." />

    <form class="max-w-3xl space-y-6" novalidate @submit.prevent="submit">
      <fieldset class="rounded-md border border-slate-200 bg-white p-5">
        <legend class="px-1 text-base font-semibold text-slate-950">Thông tin hành chính</legend>
        <div class="grid gap-4 md:grid-cols-2">
          <BaseInput id="patient-full-name" v-model="form.fullName" label="Họ tên" required :error="errors.fullName" />
          <BaseDatePicker id="patient-dob" v-model="form.dateOfBirth" label="Ngày sinh" :error="errors.dateOfBirth" />
          <BaseSelect id="patient-gender" v-model="form.genderCode" label="Giới tính" :options="genderOptions" />
          <BaseInput id="patient-phone" v-model="form.phone" label="Số điện thoại" :error="errors.phone" />
          <BaseInput id="patient-email" v-model="form.email" label="Email" type="email" :error="errors.email" />
          <BaseInput
            id="patient-insurance"
            v-model="form.healthInsuranceNumber"
            label="Số thẻ BHYT"
            hint="2 chữ cái và 8 đến 13 chữ số, ví dụ DN4010123456789"
            :error="errors.healthInsuranceNumber"
          />
          <div class="md:col-span-2">
            <BaseTextarea id="patient-address" v-model="form.address" label="Địa chỉ" :rows="2" :error="errors.address" />
          </div>
        </div>
      </fieldset>

      <fieldset class="rounded-md border border-slate-200 bg-white p-5">
        <legend class="px-1 text-base font-semibold text-slate-950">Thông tin sức khỏe tự khai</legend>
        <p class="mb-4 text-sm text-slate-600">
          Bác sĩ phụ trách sẽ xem các thông tin này trước khi khám. Lễ tân và quản trị viên không xem được.
        </p>
        <div class="grid gap-4 md:grid-cols-3">
          <BaseSelect id="patient-blood-type" v-model="form.bloodType" label="Nhóm máu" :options="bloodTypeOptions" :error="errors.bloodType" />
          <BaseInput id="patient-height" v-model="form.heightCm" label="Chiều cao (cm)" type="number" :error="errors.heightCm" />
          <BaseInput id="patient-weight" v-model="form.weightKg" label="Cân nặng (kg)" type="number" :error="errors.weightKg" />
        </div>
        <p class="mt-2 text-sm text-slate-700" aria-live="polite" data-test="bmi">
          Chỉ số BMI: <strong>{{ bmi ? `${bmi} (${bmiLabel})` : 'chưa đủ chiều cao và cân nặng' }}</strong>
        </p>
        <div class="mt-4 grid gap-4">
          <BaseTextarea id="patient-chronic" v-model="form.chronicConditions" label="Bệnh nền" :rows="2" hint="Ví dụ: tăng huyết áp, đái tháo đường, hen phế quản" :error="errors.chronicConditions" />
          <BaseTextarea id="patient-allergies" v-model="form.allergies" label="Dị ứng" :rows="2" :error="errors.allergies" />
          <BaseTextarea id="patient-family-history" v-model="form.familyHistory" label="Tiền sử bệnh trong gia đình" :rows="2" :error="errors.familyHistory" />
        </div>
      </fieldset>

      <fieldset class="rounded-md border border-slate-200 bg-white p-5">
        <legend class="px-1 text-base font-semibold text-slate-950">Người liên hệ khẩn cấp</legend>
        <div class="grid gap-4 md:grid-cols-2">
          <BaseInput id="patient-emergency-name" v-model="form.emergencyContactName" label="Họ tên người liên hệ" :error="errors.emergencyContactName" />
          <BaseInput id="patient-emergency-phone" v-model="form.emergencyContactPhone" label="Số điện thoại người liên hệ" :error="errors.emergencyContactPhone" />
        </div>
      </fieldset>

      <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <p v-if="successMessage" class="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">{{ successMessage }}</p>

      <div class="flex justify-end">
        <BaseButton type="submit" :loading="saving">
          <i class="fa-solid fa-floppy-disk" aria-hidden="true"></i>
          Lưu hồ sơ
        </BaseButton>
      </div>
    </form>
  </PatientLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, BaseTextarea, PageHeader } from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { patientsApi } from '@/api/patients'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { fieldErrors } from '@/utils/params'

const genderOptions = [
  { label: 'Chưa chọn', value: '' },
  { label: 'Nam', value: 'MALE' },
  { label: 'Nữ', value: 'FEMALE' },
  { label: 'Khác', value: 'OTHER' }
]
const bloodTypeOptions = [
  { label: 'Chưa rõ', value: '' },
  ...['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-', 'A', 'B', 'AB', 'O'].map((value) => ({ label: value, value }))
]
// Trường văn bản tự do: gửi chuỗi rỗng để xóa giá trị đã khai (máy chủ coi null là giữ nguyên)
const TEXT_FIELDS = ['fullName', 'phone', 'email', 'address', 'bloodType', 'allergies', 'emergencyContactName',
  'emergencyContactPhone', 'healthInsuranceNumber', 'chronicConditions', 'familyHistory']

const form = reactive({
  fullName: '',
  dateOfBirth: '',
  genderCode: '',
  phone: '',
  email: '',
  address: '',
  bloodType: '',
  allergies: '',
  heightCm: '',
  weightKg: '',
  healthInsuranceNumber: '',
  chronicConditions: '',
  familyHistory: '',
  emergencyContactName: '',
  emergencyContactPhone: ''
})
const errors = reactive({})
const saving = ref(false)
const error = ref('')
const successMessage = ref('')

const bmi = computed(() => {
  const height = Number(form.heightCm) / 100
  const weight = Number(form.weightKg)
  if (!height || !weight) return null
  return (weight / (height * height)).toFixed(1)
})
// Ngưỡng BMI của WHO cho người châu Á
const bmiLabel = computed(() => {
  const value = Number(bmi.value)
  if (value < 18.5) return 'thiếu cân'
  if (value < 23) return 'bình thường'
  if (value < 25) return 'thừa cân'
  return 'béo phì'
})

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
      allergies: profile.allergies || '',
      heightCm: profile.heightCm ?? '',
      weightKg: profile.weightKg ?? '',
      healthInsuranceNumber: profile.healthInsuranceNumber || '',
      chronicConditions: profile.chronicConditions || '',
      familyHistory: profile.familyHistory || '',
      emergencyContactName: profile.emergencyContactName || '',
      emergencyContactPhone: profile.emergencyContactPhone || ''
    })
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
})

function payload() {
  const body = {}
  for (const field of TEXT_FIELDS) body[field] = form[field]
  // Chuỗi rỗng ở các trường có định dạng (điện thoại) bị máy chủ coi là sai: bỏ khỏi yêu cầu để giữ nguyên
  for (const field of ['phone', 'emergencyContactPhone', 'email', 'bloodType']) {
    if (!String(body[field] ?? '').trim()) delete body[field]
  }
  body.genderCode = form.genderCode || undefined
  body.dateOfBirth = form.dateOfBirth || undefined
  body.heightCm = form.heightCm === '' ? undefined : Number(form.heightCm)
  body.weightKg = form.weightKg === '' ? undefined : Number(form.weightKg)
  return body
}

async function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  successMessage.value = ''
  Object.keys(errors).forEach((key) => delete errors[key])
  try {
    await patientsApi.updateMe(payload())
    successMessage.value = 'Đã lưu hồ sơ.'
  } catch (err) {
    Object.assign(errors, fieldErrors(err))
    error.value = apiErrorMessage(err)
  } finally {
    saving.value = false
  }
}
</script>
