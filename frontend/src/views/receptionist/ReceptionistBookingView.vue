<template>
  <ReceptionistLayout>
    <div class="space-y-6">
      <PageHeader title="Đặt lịch hộ" eyebrow="Quầy lễ tân" description="Chọn bệnh nhân, dịch vụ, bác sĩ và khung giờ để tạo lịch hẹn tại quầy." />

      <ol class="grid gap-2 text-sm sm:grid-cols-4" aria-label="Các bước đặt lịch">
        <li v-for="item in stepLabels" :key="item.step" class="rounded-md border px-3 py-2" :class="step === item.step ? 'border-primary-600 bg-primary-50 text-primary-800' : 'border-slate-200 bg-white text-slate-600'">
          <span class="font-semibold">Bước {{ item.step }}.</span> {{ item.label }}
        </li>
      </ol>

      <section v-if="pageError" class="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p class="text-sm font-medium text-red-700">{{ pageError }}</p>
      </section>

      <section v-if="createdAppointment" class="rounded-md border border-emerald-200 bg-emerald-50 p-5">
        <h2 class="text-lg font-semibold text-emerald-950">Đã đặt lịch</h2>
        <p class="mt-2 text-sm text-emerald-800">Mã lịch #{{ createdAppointment.id }} · {{ selectedPatient?.fullName }} · {{ selectedService?.name }}</p>
        <div class="mt-4 flex flex-wrap gap-2">
          <BaseButton variant="secondary" @click="bookAnother">Đặt lịch khác</BaseButton>
          <RouterLink to="/receptionist/appointments" class="inline-flex min-h-10 items-center justify-center rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700">
            Về danh sách lịch hẹn
          </RouterLink>
        </div>
      </section>

      <template v-else>
        <section v-if="step === 1" class="space-y-4 rounded-md border border-slate-200 bg-white p-5">
          <form class="flex flex-wrap items-end gap-3" @submit.prevent="searchPatients">
            <BaseInput id="booking-patient-keyword" v-model="patientKeyword" class="min-w-0 flex-1" label="Tìm bệnh nhân" placeholder="Họ tên, mã bệnh nhân, điện thoại" />
            <BaseButton type="submit" :loading="searchingPatients" icon="fa-solid fa-magnifying-glass">Tìm</BaseButton>
            <BaseButton variant="secondary" icon="fa-solid fa-user-plus" @click="patientFormOpen = true">Tạo hồ sơ mới</BaseButton>
          </form>
          <p v-if="patientError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ patientError }}</p>
          <div v-if="selectedPatient" class="rounded-md border border-primary-200 bg-primary-50 p-4">
            <p class="font-semibold text-primary-950">{{ selectedPatient.fullName }}</p>
            <p class="text-sm text-primary-800">{{ selectedPatient.patientCode }} · {{ selectedPatient.phone }}</p>
          </div>
          <DataTable :columns="patientColumns" :rows="patientResults" :loading="searchingPatients" empty-text="Nhập từ khóa để tìm bệnh nhân">
            <template #cell-fullName="{ row }">
              <div class="font-semibold text-slate-950">{{ row.fullName }}</div>
              <div class="text-xs text-slate-500">{{ row.patientCode }}</div>
            </template>
            <template #cell-dateOfBirth="{ value }">{{ formatDate(value) }}</template>
            <template #cell-actions="{ row }">
              <BaseButton variant="secondary" @click="selectPatient(row)">Chọn</BaseButton>
            </template>
          </DataTable>
          <div class="flex justify-end">
            <BaseButton :disabled="!selectedPatient" @click="step = 2">Tiếp tục</BaseButton>
          </div>
        </section>

        <section v-if="step === 2" class="space-y-4 rounded-md border border-slate-200 bg-white p-5">
          <BaseSelect id="booking-service" v-model="serviceId" label="Dịch vụ khám" placeholder="Chọn dịch vụ" required :options="serviceOptions" />
          <div v-if="selectedService" class="rounded-md bg-slate-50 p-4 text-sm text-slate-700">
            <p class="font-semibold text-slate-950">{{ selectedService.name }}</p>
            <p>{{ selectedService.durationMinutes }} phút · {{ formatCurrency(selectedService.price) }}</p>
          </div>

          <section v-if="selectedService" class="rounded-md border border-slate-200 p-4" aria-labelledby="receptionist-earliest-title" data-test="earliest">
            <h2 id="receptionist-earliest-title" class="font-semibold text-slate-950">Giờ sớm nhất còn trống</h2>
            <div class="mt-3 max-w-md">
              <BaseSelect
                id="booking-earliest-specialty"
                v-model="specialtyId"
                label="Chuyên khoa"
                placeholder="Tất cả chuyên khoa"
                :options="specialtyOptions"
              />
            </div>
            <p v-if="loadingEarliest" class="mt-3 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">Đang tìm giờ sớm nhất...</p>
            <div v-else-if="earliestError" class="mt-3 rounded-md bg-red-50 px-3 py-3 text-sm text-red-700" role="alert">
              <p>{{ earliestError }}</p>
              <BaseButton class="mt-2" variant="secondary" @click="loadEarliest">Thử lại</BaseButton>
            </div>
            <p v-else-if="!earliest.length" class="mt-3 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">
              Chưa có khung giờ trống phù hợp trong 30 ngày tới.
            </p>
            <ul v-else class="mt-3 grid gap-2 md:grid-cols-2" aria-label="Giờ sớm nhất còn trống">
              <li v-for="item in earliest" :key="item.slotId">
                <button
                  type="button"
                  class="w-full rounded-md border border-slate-300 bg-white p-3 text-left transition hover:border-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-600/40 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="choosingEarliest"
                  @click="pickEarliest(item)"
                >
                  <span class="block font-semibold text-slate-950">
                    {{ formatDate(item.date) }} · {{ formatTime(item.startTime) }} - {{ formatTime(item.endTime) }}
                  </span>
                  <span class="block text-sm text-slate-600">
                    {{ item.doctorName }}<template v-if="item.specialtyName"> · {{ item.specialtyName }}</template>
                  </span>
                </button>
              </li>
            </ul>
          </section>

          <div class="flex justify-between gap-2">
            <BaseButton variant="secondary" @click="step = 1">Quay lại</BaseButton>
            <BaseButton :disabled="!selectedService" @click="step = 3">Tiếp tục</BaseButton>
          </div>
        </section>

        <section v-if="step === 3" class="space-y-4 rounded-md border border-slate-200 bg-white p-5">
          <div class="grid gap-4 md:grid-cols-3">
            <BaseSelect id="booking-specialty" v-model="specialtyId" label="Chuyên khoa" placeholder="Tất cả" :options="specialtyOptions" />
            <BaseSelect id="booking-doctor" v-model="doctorId" label="Bác sĩ" placeholder="Chọn bác sĩ" required :options="doctorOptions" />
            <BaseDatePicker id="booking-date" v-model="appointmentDate" label="Ngày khám" required :error="dateError" />
          </div>
          <SlotPicker
            :key="slotPickerKey"
            v-model="slotId"
            :doctor-id="doctorId"
            :date="appointmentDate"
            :service-id="serviceId || null"
            :refresh-key="refreshKey"
            @select="holdBookingSlot"
            @update:model-value="onSlotModelUpdate"
          />
          <div
            v-if="slotHold.hold.value"
            class="rounded-md border border-primary-200 bg-primary-50 px-3 py-2 text-sm text-primary-900"
            data-test="hold-countdown"
          >
            <span aria-hidden="true">Đang giữ chỗ, còn <strong>{{ formatHoldCountdown(slotHold.remainingSeconds.value) }}</strong></span>
            <span class="sr-only" aria-live="polite">{{ holdAnnouncement }}</span>
          </div>
          <p v-if="slotHold.holdError.value" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {{ slotHold.holdError.value }}
          </p>
          <div class="flex justify-between gap-2">
            <BaseButton variant="secondary" @click="step = 2">Quay lại</BaseButton>
            <BaseButton :disabled="!canConfirm" @click="step = 4">Tiếp tục</BaseButton>
          </div>
        </section>

        <section v-if="step === 4" class="space-y-4 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-950">Xác nhận đặt lịch</h2>
          <dl class="grid gap-3 text-sm md:grid-cols-2">
            <div><dt class="text-slate-500">Bệnh nhân</dt><dd class="font-semibold text-slate-950">{{ selectedPatient?.fullName }}</dd></div>
            <div><dt class="text-slate-500">Dịch vụ</dt><dd class="font-semibold text-slate-950">{{ selectedService?.name }} · {{ formatCurrency(selectedService?.price) }}</dd></div>
            <div><dt class="text-slate-500">Bác sĩ</dt><dd class="font-semibold text-slate-950">{{ selectedDoctor?.fullName }}</dd></div>
            <div><dt class="text-slate-500">Thời gian</dt><dd class="font-semibold text-slate-950">{{ formatDate(appointmentDate) }} · {{ slotSummary }}</dd></div>
          </dl>
          <div
            v-if="slotHold.hold.value"
            class="rounded-md border border-primary-200 bg-primary-50 px-3 py-2 text-sm text-primary-900"
            data-test="hold-countdown"
          >
            <span aria-hidden="true">Đang giữ chỗ, còn <strong>{{ formatHoldCountdown(slotHold.remainingSeconds.value) }}</strong></span>
            <span class="sr-only" aria-live="polite">{{ holdAnnouncement }}</span>
          </div>
          <BaseTextarea id="booking-notes" v-model="notes" label="Ghi chú khi đặt hộ" :rows="4" :error="notesError" hint="Tối đa 2000 ký tự. Ghi chú chỉ gửi một chiều, không đọc lại từ máy chủ." />
          <p class="text-right text-xs text-slate-500">{{ notes.length }}/2000</p>
          <p v-if="submitError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ submitError }}</p>
          <div class="flex justify-between gap-2">
            <BaseButton variant="secondary" @click="step = 3">Quay lại</BaseButton>
            <BaseButton :loading="submitting" :disabled="submitting" @click="submitBooking">Xác nhận đặt lịch</BaseButton>
          </div>
        </section>
      </template>
    </div>

    <PatientFormModal v-model="patientFormOpen" @created="selectPatient" />
  </ReceptionistLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, BaseTextarea, DataTable, PageHeader } from '@/components/ui'
import ReceptionistLayout from '@/layouts/ReceptionistLayout.vue'
import PatientFormModal from './PatientFormModal.vue'
import SlotPicker from '@/views/patient/booking/SlotPicker.vue'
import { doctorName, doctorSpecialty, normalizeList, slotLabel, todayIso } from '@/views/patient/booking/bookingUtils'
import { receptionApi } from '@/api/reception'
import { slotsApi } from '@/api/slots'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { fieldErrors } from '@/utils/params'
import { formatHoldCountdown, useSlotHold } from '@/views/patient/booking/useSlotHold'
import { formatCurrency, formatDate, formatTime } from '@/utils/formatters'

const route = useRoute()
const stepLabels = [
  { step: 1, label: 'Bệnh nhân' },
  { step: 2, label: 'Dịch vụ' },
  { step: 3, label: 'Bác sĩ và giờ' },
  { step: 4, label: 'Xác nhận' }
]
const patientColumns = [
  { key: 'fullName', label: 'Bệnh nhân' },
  { key: 'dateOfBirth', label: 'Ngày sinh' },
  { key: 'phone', label: 'Điện thoại' },
  { key: 'actions', label: 'Thao tác' }
]

const step = ref(1)
const pageError = ref('')
const patientKeyword = ref('')
const patientResults = ref([])
const searchingPatients = ref(false)
const patientError = ref('')
const selectedPatient = ref(null)
const patientFormOpen = ref(false)

const services = ref([])
const specialties = ref([])
const doctors = ref([])
const serviceId = ref('')
const specialtyId = ref('')
const doctorId = ref('')
const appointmentDate = ref(todayIso())
const slotId = ref(null)
const selectedSlot = ref(null)
const refreshKey = ref(0)
const notes = ref('')
const notesError = ref('')
const submitError = ref('')
const submitting = ref(false)
const createdAppointment = ref(null)
const earliest = ref([])
const loadingEarliest = ref(false)
const earliestError = ref('')
const choosingEarliest = ref(false)
const slotHold = useSlotHold()

const EARLIEST_LIMIT = 5
const HOLD_EXPIRED_MESSAGE = 'Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ.'
const SLOT_TAKEN_MESSAGE = 'Khung giờ vừa được người khác đặt hoặc giữ. Vui lòng chọn khung giờ khác.'
let earliestRequest = 0

const serviceOptions = computed(() => services.value.map((item) => ({ label: item.name, value: String(item.id) })))
const specialtyOptions = computed(() => specialties.value.map((item) => ({ label: item.name, value: String(item.id) })))
const selectedService = computed(() => services.value.find((item) => String(item.id) === String(serviceId.value)) || null)
const filteredDoctors = computed(() => doctors.value.filter((doctor) => !specialtyId.value || String(doctor.specialtyId) === String(specialtyId.value)))
const doctorOptions = computed(() => filteredDoctors.value.map((doctor) => ({ label: `${doctorName(doctor)}${doctorSpecialty(doctor) ? ` · ${doctorSpecialty(doctor)}` : ''}`, value: String(doctor.id) })))
const selectedDoctor = computed(() => doctors.value.find((item) => String(item.id) === String(doctorId.value)) || null)
const dateError = computed(() => appointmentDate.value && appointmentDate.value < todayIso() ? 'Không chọn ngày đã qua.' : '')
const canConfirm = computed(() => selectedService.value && selectedDoctor.value && appointmentDate.value && !dateError.value && slotId.value && !slotHold.holding.value)
const slotSummary = computed(() => selectedSlot.value ? slotLabel(selectedSlot.value) : `Khung giờ #${slotId.value || ''}`)
const slotPickerKey = computed(() => `${serviceId.value}-${doctorId.value}-${appointmentDate.value}-${refreshKey.value}`)
const holdAnnouncement = computed(() => {
  const seconds = slotHold.remainingSeconds.value
  if (!slotHold.hold.value) return ''
  if (seconds > 30) return `Đang giữ chỗ, còn khoảng ${Math.ceil(seconds / 60)} phút`
  return seconds > 10 ? 'Đang giữ chỗ, còn chưa đầy 30 giây' : 'Đang giữ chỗ, còn chưa đầy 10 giây'
})

function clearCurrentSlot({ release = true } = {}) {
  slotId.value = null
  selectedSlot.value = null
  if (release) slotHold.releaseHold()
}

function backToSlots(message) {
  pageError.value = message
  submitError.value = message
  clearCurrentSlot()
  refreshKey.value += 1
  step.value = 3
}

async function holdBookingSlot(slot) {
  selectedSlot.value = slot
  slotId.value = slot.id
  pageError.value = ''
  submitError.value = ''
  const held = await slotHold.holdSlot(slot.id)
  if (String(slotId.value) !== String(slot.id)) return false
  if (!held) {
    backToSlots(slotHold.holdError.value || SLOT_TAKEN_MESSAGE)
    return false
  }
  return true
}

function onSlotModelUpdate(value) {
  if (value === null) clearCurrentSlot()
}

watch([serviceId, doctorId, appointmentDate], () => {
  clearCurrentSlot()
})

watch(slotHold.expired, (expired) => {
  if (!expired || !slotId.value || createdAppointment.value) return
  backToSlots(HOLD_EXPIRED_MESSAGE)
})

watch([serviceId, specialtyId], loadEarliest)

async function loadEarliest() {
  earliest.value = []
  earliestError.value = ''
  if (!selectedService.value) return
  const current = ++earliestRequest
  loadingEarliest.value = true
  try {
    const response = await slotsApi.earliest({
      serviceId: Number(serviceId.value),
      specialtyId: specialtyId.value || undefined,
      limit: EARLIEST_LIMIT
    })
    if (current === earliestRequest) earliest.value = normalizeList(response).slice(0, EARLIEST_LIMIT)
  } catch (err) {
    if (current === earliestRequest) earliestError.value = apiErrorMessage(err)
  } finally {
    if (current === earliestRequest) loadingEarliest.value = false
  }
}

async function pickEarliest(item) {
  if (choosingEarliest.value) return
  choosingEarliest.value = true
  try {
    clearCurrentSlot()
    let doctor = doctors.value.find((candidate) => String(candidate.id) === String(item.doctorId))
    if (!doctor) {
      doctor = { id: item.doctorId, fullName: item.doctorName, specialtyId: item.specialtyId, specialtyName: item.specialtyName }
      doctors.value = [...doctors.value, doctor]
    }
    doctorId.value = String(item.doctorId)
    appointmentDate.value = item.date
    await nextTick()
    const held = await holdBookingSlot({
      id: item.slotId,
      doctorId: item.doctorId,
      slotDate: item.date,
      date: item.date,
      startTime: item.startTime,
      endTime: item.endTime
    })
    if (held) step.value = 4
    else loadEarliest()
  } finally {
    choosingEarliest.value = false
  }
}

async function loadLookups() {
  try {
    const [serviceResponse, specialtyResponse, doctorResponse] = await Promise.all([
      catalogApi.services.list({ size: 100 }),
      catalogApi.specialties.list({ size: 100 }),
      doctorsApi.list({ size: 100 })
    ])
    services.value = serviceResponse?.content ?? []
    specialties.value = specialtyResponse?.content ?? []
    doctors.value = doctorResponse?.content ?? []
  } catch (err) {
    pageError.value = apiErrorMessage(err)
  }
}

async function loadPatientFromQuery() {
  const id = route.query.patientId
  if (!id) return
  try {
    selectedPatient.value = await receptionApi.patients.get(id)
    step.value = 2
  } catch (err) {
    patientError.value = err?.status === 404 ? 'Không tìm thấy bệnh nhân, vui lòng chọn lại.' : apiErrorMessage(err)
    step.value = 1
  }
}

async function searchPatients() {
  searchingPatients.value = true
  patientError.value = ''
  try {
    const response = await receptionApi.patients.list({ keyword: patientKeyword.value, page: 0, size: 10 })
    patientResults.value = response?.content ?? []
  } catch (err) {
    patientResults.value = []
    patientError.value = apiErrorMessage(err)
  } finally {
    searchingPatients.value = false
  }
}

function selectPatient(patient) {
  if (selectedPatient.value?.id !== patient?.id) clearCurrentSlot()
  selectedPatient.value = patient
  patientFormOpen.value = false
}

function validateNotes() {
  notesError.value = notes.value.length > 2000 ? 'Ghi chú tối đa 2000 ký tự.' : ''
  return !notesError.value
}

async function submitBooking() {
  if (submitting.value || createdAppointment.value || !validateNotes()) return
  submitting.value = true
  submitError.value = ''
  pageError.value = ''
  const payload = {
    patientId: selectedPatient.value.id,
    slotId: Number(slotId.value),
    serviceId: Number(serviceId.value)
  }
  payload.holdId = slotHold.hold.value?.holdId
  if (notes.value.trim()) payload.notes = notes.value.trim()
  try {
    createdAppointment.value = await receptionApi.appointments.create(payload)
    slotHold.forgetHold()
  } catch (err) {
    const details = fieldErrors(err)
    notesError.value = details.notes || ''
    submitError.value = apiErrorMessage(err)
    if (err?.status === 409 || err?.status === 410 || err?.status === 422) {
      backToSlots(submitError.value)
    }
  } finally {
    submitting.value = false
  }
}

function bookAnother() {
  slotHold.releaseHold()
  createdAppointment.value = null
  serviceId.value = ''
  specialtyId.value = ''
  doctorId.value = ''
  appointmentDate.value = todayIso()
  slotId.value = null
  selectedSlot.value = null
  notes.value = ''
  submitError.value = ''
  step.value = 2
}

onMounted(async () => {
  await Promise.all([loadLookups(), loadPatientFromQuery()])
})
</script>
