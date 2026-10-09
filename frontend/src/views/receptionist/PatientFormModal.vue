<template>
  <FormModal v-model="open" title="Thêm bệnh nhân" description="Tạo hồ sơ hành chính cho bệnh nhân tại quầy.">
    <form class="grid gap-4 sm:grid-cols-2" novalidate @submit.prevent="submit">
      <BaseInput id="patient-full-name" v-model="form.fullName" label="Họ tên" required :error="errors.fullName" />
      <BaseDatePicker id="patient-date-of-birth" v-model="form.dateOfBirth" label="Ngày sinh" required :error="errors.dateOfBirth" />
      <BaseSelect id="patient-gender" v-model="form.genderCode" label="Giới tính" required :options="genderOptions" :error="errors.genderCode" />
      <BaseInput id="patient-phone" v-model="form.phone" label="Điện thoại" required :error="errors.phone" />
      <BaseInput id="patient-email" v-model="form.email" label="Email" type="email" :error="errors.email" />
      <BaseInput id="patient-address" v-model="form.address" label="Địa chỉ" :error="errors.address" />
    </form>
    <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>
    <template #footer>
      <BaseButton variant="secondary" @click="open = false">Hủy</BaseButton>
      <BaseButton :loading="submitting" @click="submit">Lưu</BaseButton>
    </template>
  </FormModal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, FormModal } from '@/components/ui'
import { receptionApi } from '@/api/reception'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { fieldErrors } from '@/utils/params'
import { todayIso } from '@/views/patient/booking/bookingUtils'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'created'])
const { success } = useToast()

const genderOptions = [
  { label: 'Nam', value: 'MALE' },
  { label: 'Nữ', value: 'FEMALE' },
  { label: 'Khác', value: 'OTHER' }
]

const emptyForm = () => ({ fullName: '', dateOfBirth: '', genderCode: '', phone: '', email: '', address: '' })
const form = reactive(emptyForm())
const errors = ref({})
const formError = ref('')
const submitting = ref(false)

const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      Object.assign(form, emptyForm())
      errors.value = {}
      formError.value = ''
    }
  }
)

function validate() {
  const next = {}
  const phonePattern = /^[0-9+()\-\s]{8,30}$/
  if (!form.fullName.trim()) next.fullName = 'Họ tên là bắt buộc.'
  else if (form.fullName.trim().length > 150) next.fullName = 'Họ tên tối đa 150 ký tự.'
  if (!form.dateOfBirth) next.dateOfBirth = 'Ngày sinh là bắt buộc.'
  else if (form.dateOfBirth > todayIso()) next.dateOfBirth = 'Ngày sinh không được ở tương lai.'
  if (!form.genderCode) next.genderCode = 'Giới tính là bắt buộc.'
  if (!form.phone.trim()) next.phone = 'Điện thoại là bắt buộc.'
  else if (!phonePattern.test(form.phone.trim())) next.phone = 'Điện thoại phải có 8-30 ký tự hợp lệ.'
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Email không đúng định dạng.'
  if (form.email.trim().length > 190) next.email = 'Email tối đa 190 ký tự.'
  if (form.address.trim().length > 255) next.address = 'Địa chỉ tối đa 255 ký tự.'
  errors.value = next
  return Object.keys(next).length === 0
}

async function submit() {
  if (submitting.value || !validate()) return
  submitting.value = true
  formError.value = ''
  errors.value = {}
  const payload = {
    fullName: form.fullName.trim(),
    dateOfBirth: form.dateOfBirth,
    genderCode: form.genderCode,
    phone: form.phone.trim(),
    email: form.email.trim(),
    address: form.address.trim()
  }
  try {
    const created = await receptionApi.patients.create(payload)
    success('Đã thêm bệnh nhân')
    emit('created', created)
    open.value = false
  } catch (err) {
    const details = fieldErrors(err)
    errors.value = { ...details }
    if (err?.status === 409) {
      errors.value.phone = details.phone || `${apiErrorMessage(err)} Hãy tìm hồ sơ đã có trước khi tạo mới.`
    }
    formError.value = apiErrorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>
