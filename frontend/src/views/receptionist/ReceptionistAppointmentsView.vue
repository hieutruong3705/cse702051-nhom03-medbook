<template>
  <ReceptionistLayout>
    <div class="space-y-6">
      <PageHeader title="Lịch hẹn" eyebrow="Quầy lễ tân" description="Theo dõi lịch hẹn toàn phòng khám và hỗ trợ hủy hoặc đổi lịch cho bệnh nhân." />

      <form class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 lg:grid-cols-6" @submit.prevent="reload">
        <BaseDatePicker id="appointment-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="appointment-to" v-model="filters.to" label="Đến ngày" />
        <BaseSelect id="appointment-doctor" v-model="filters.doctorId" label="Bác sĩ" placeholder="Tất cả" :options="doctorOptions" />
        <BaseSelect id="appointment-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <BaseInput id="appointment-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Tên, mã, điện thoại" />
        <div class="flex items-end gap-2">
          <BaseButton type="submit" :loading="loading" icon="fa-solid fa-filter">Lọc</BaseButton>
          <BaseButton type="button" variant="secondary" @click="resetToday">Hôm nay</BaseButton>
        </div>
      </form>

      <section v-if="error" class="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p class="text-sm font-medium text-red-700">{{ error }}</p>
        <BaseButton class="mt-3" variant="secondary" icon="fa-solid fa-rotate-right" @click="load">Thử lại</BaseButton>
      </section>

      <DataTable v-else :columns="columns" :rows="appointments" :loading="loading" empty-text="Chưa có lịch hẹn phù hợp">
        <template #cell-time="{ row }">
          <div>
            <p class="font-semibold text-slate-950">{{ formatDate(row.date) }}</p>
            <p class="text-xs text-slate-500">{{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}</p>
          </div>
        </template>
        <template #cell-patient="{ row }">
          <div class="font-semibold text-slate-950">{{ row.patientName }}</div>
          <div class="text-xs text-slate-500">{{ row.patientCode }} · {{ row.patientPhone }}</div>
        </template>
        <template #cell-doctor="{ row }">
          <div class="font-semibold text-slate-950">{{ row.doctorName }}</div>
          <div class="text-xs text-slate-500">{{ row.specialtyName }}</div>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="appointmentStatus[row.status] || row.status" />
        </template>
        <template #cell-actions="{ row }">
          <div v-if="row.status === 'BOOKED'" class="flex flex-wrap gap-2">
            <BaseButton variant="secondary" :aria-label="`Đổi lịch #${row.id}`" @click="openReschedule(row)">Đổi lịch</BaseButton>
            <BaseButton variant="danger" :aria-label="`Hủy lịch #${row.id}`" @click="openCancel(row)">Hủy</BaseButton>
          </div>
          <span v-else class="text-sm text-slate-500">Không có</span>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </div>

    <FormModal v-model="cancelOpen" title="Hủy lịch hẹn" :description="cancelDescription">
      <BaseTextarea id="cancel-reason" v-model="cancelReason" label="Lý do hủy" required :rows="4" :error="cancelErrors.reason" />
      <p class="mt-1 text-right text-xs text-slate-500">{{ cancelReason.length }}/500</p>
      <p v-if="actionError" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ actionError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="cancelOpen = false">Hủy</BaseButton>
        <BaseButton variant="danger" :loading="acting" @click="submitCancel">Hủy lịch hẹn</BaseButton>
      </template>
    </FormModal>

    <FormModal v-model="rescheduleOpen" title="Đổi lịch hẹn" :description="rescheduleDescription">
      <div class="grid gap-4 sm:grid-cols-2">
        <BaseSelect id="reschedule-doctor" v-model="rescheduleDoctorId" label="Bác sĩ" required :options="doctorOptions" />
        <BaseDatePicker id="reschedule-date" v-model="rescheduleDate" label="Ngày khám" required />
      </div>
      <div class="mt-4">
        <SlotPicker
          v-model="newSlotId"
          :doctor-id="rescheduleDoctorId"
          :date="rescheduleDate"
          :service-id="selected?.serviceId || null"
          :refresh-key="refreshKey"
          @select="holdRescheduleSlot"
          @update:model-value="onRescheduleSlotModelUpdate"
        />
        <div
          v-if="rescheduleHold.hold.value"
          class="mt-3 rounded-md border border-primary-200 bg-primary-50 px-3 py-2 text-sm text-primary-900"
          data-test="reschedule-hold-countdown"
        >
          <span aria-hidden="true">Đang giữ chỗ, còn <strong>{{ formatHoldCountdown(rescheduleHold.remainingSeconds.value) }}</strong></span>
          <span class="sr-only" aria-live="polite">{{ rescheduleHoldAnnouncement }}</span>
        </div>
        <p v-if="rescheduleHold.holdError.value" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {{ rescheduleHold.holdError.value }}
        </p>
      </div>
      <BaseTextarea id="reschedule-reason" v-model="rescheduleReason" class="mt-4" label="Lý do đổi lịch" required :rows="4" :error="rescheduleErrors.reason || rescheduleErrors.newSlotId" />
      <p class="mt-1 text-right text-xs text-slate-500">{{ rescheduleReason.length }}/500</p>
      <p v-if="actionError" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ actionError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="rescheduleOpen = false">Hủy</BaseButton>
        <BaseButton :loading="acting" @click="submitReschedule">Đổi lịch</BaseButton>
      </template>
    </FormModal>
  </ReceptionistLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, BaseTextarea, DataTable, FormModal, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import ReceptionistLayout from '@/layouts/ReceptionistLayout.vue'
import SlotPicker from '@/views/patient/booking/SlotPicker.vue'
import { todayIso } from '@/views/patient/booking/bookingUtils'
import { receptionApi } from '@/api/reception'
import { doctorsApi } from '@/api/doctors'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams, fieldErrors } from '@/utils/params'
import { formatDate, formatTime } from '@/utils/formatters'
import { appointmentStatus } from '@/utils/statusLabels'
import { formatHoldCountdown, useSlotHold } from '@/views/patient/booking/useSlotHold'

const statusOptions = [
  { label: 'Đã đặt', value: 'BOOKED' },
  { label: 'Đang khám', value: 'IN_PROGRESS' },
  { label: 'Hoàn thành', value: 'COMPLETED' },
  { label: 'Đã hủy', value: 'CANCELLED' }
]
const columns = [
  { key: 'time', label: 'Giờ' },
  { key: 'patient', label: 'Bệnh nhân' },
  { key: 'doctor', label: 'Bác sĩ' },
  { key: 'serviceName', label: 'Dịch vụ' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const filters = reactive({ from: todayIso(), to: todayIso(), doctorId: '', status: '', keyword: '' })
const appointments = ref([])
const doctors = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })
const { success, error: toastError } = useToast()

const selected = ref(null)
const cancelOpen = ref(false)
const cancelReason = ref('')
const cancelErrors = ref({})
const rescheduleOpen = ref(false)
const rescheduleDoctorId = ref('')
const rescheduleDate = ref(todayIso())
const newSlotId = ref(null)
const rescheduleReason = ref('')
const rescheduleErrors = ref({})
const refreshKey = ref(0)
const actionError = ref('')
const acting = ref(false)
const rescheduleHold = useSlotHold()

const doctorOptions = computed(() => doctors.value.map((doctor) => ({ label: `${doctor.fullName}${doctor.specialtyName ? ` · ${doctor.specialtyName}` : ''}`, value: String(doctor.id) })))
const cancelDescription = computed(() => selected.value ? `${selected.value.patientName} · ${formatDate(selected.value.date)} ${formatTime(selected.value.startTime)}` : '')
const rescheduleDescription = computed(() => selected.value ? `${selected.value.patientName} · lịch hiện tại ${formatDate(selected.value.date)} ${formatTime(selected.value.startTime)}` : '')
const rescheduleHoldAnnouncement = computed(() => {
  const seconds = rescheduleHold.remainingSeconds.value
  if (!rescheduleHold.hold.value) return ''
  if (seconds > 30) return `Đang giữ chỗ, còn khoảng ${Math.ceil(seconds / 60)} phút`
  return seconds > 10 ? 'Đang giữ chỗ, còn chưa đầy 30 giây' : 'Đang giữ chỗ, còn chưa đầy 10 giây'
})

async function loadDoctors() {
  const response = await doctorsApi.list({ size: 100 })
  doctors.value = response?.content ?? []
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await receptionApi.appointments.list(cleanParams({ ...filters, page: page.value, size: size.value }))
    appointments.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    appointments.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  load()
}

function changePage(next) {
  page.value = next
  load()
}

function resetToday() {
  Object.assign(filters, { from: todayIso(), to: todayIso(), doctorId: '', status: '', keyword: '' })
  reload()
}

function updateRow(row) {
  const index = appointments.value.findIndex((item) => item.id === row.id)
  if (index >= 0) appointments.value.splice(index, 1, row)
}

function openCancel(row) {
  selected.value = row
  cancelReason.value = ''
  cancelErrors.value = {}
  actionError.value = ''
  cancelOpen.value = true
}

function openReschedule(row) {
  rescheduleHold.releaseHold()
  selected.value = row
  rescheduleDoctorId.value = String(row.doctorId)
  rescheduleDate.value = row.date
  newSlotId.value = null
  rescheduleReason.value = ''
  rescheduleErrors.value = {}
  actionError.value = ''
  rescheduleOpen.value = true
}

function clearRescheduleSlot({ release = true } = {}) {
  newSlotId.value = null
  if (release) rescheduleHold.releaseHold()
}

async function holdRescheduleSlot(slot) {
  newSlotId.value = slot.id
  actionError.value = ''
  const held = await rescheduleHold.holdSlot(slot.id)
  if (String(newSlotId.value) !== String(slot.id)) return false
  if (!held) {
    actionError.value = rescheduleHold.holdError.value || 'Khung giờ vừa được người khác đặt hoặc giữ. Vui lòng chọn khung giờ khác.'
    clearRescheduleSlot({ release: false })
    refreshKey.value += 1
    return false
  }
  return true
}

function onRescheduleSlotModelUpdate(value) {
  if (value === null) clearRescheduleSlot()
}

function validateReason(reason, target) {
  const text = reason.trim()
  if (!text) target.value = { reason: 'Phải nhập lý do.' }
  else if (text.length > 500) target.value = { reason: 'Lý do tối đa 500 ký tự.' }
  else target.value = {}
  return !target.value.reason
}

async function submitCancel() {
  if (!selected.value || acting.value || !validateReason(cancelReason.value, cancelErrors)) return
  acting.value = true
  actionError.value = ''
  try {
    const updated = await receptionApi.appointments.cancel(selected.value.id, { reason: cancelReason.value.trim() })
    updateRow(updated)
    success('Đã hủy lịch hẹn')
    cancelOpen.value = false
  } catch (err) {
    cancelErrors.value = fieldErrors(err)
    actionError.value = apiErrorMessage(err)
    if (err?.status === 409 || err?.status === 422) {
      toastError(actionError.value)
      await load()
    }
  } finally {
    acting.value = false
  }
}

async function submitReschedule() {
  if (!selected.value || acting.value) return
  const validReason = validateReason(rescheduleReason.value, rescheduleErrors)
  if (!newSlotId.value) rescheduleErrors.value = { ...rescheduleErrors.value, newSlotId: 'Phải chọn khung giờ mới.' }
  if (!validReason || !newSlotId.value) return
  acting.value = true
  actionError.value = ''
  try {
    const updated = await receptionApi.appointments.reschedule(selected.value.id, {
      newSlotId: Number(newSlotId.value),
      reason: rescheduleReason.value.trim(),
      holdId: rescheduleHold.hold.value?.holdId
    })
    updateRow(updated)
    success('Đã đổi lịch hẹn')
    rescheduleHold.forgetHold()
    rescheduleOpen.value = false
  } catch (err) {
    rescheduleErrors.value = fieldErrors(err)
    actionError.value = apiErrorMessage(err)
    if (err?.status === 409 || err?.status === 410 || err?.status === 422) {
      clearRescheduleSlot()
      refreshKey.value += 1
    }
    if (err?.status === 422) {
      await load()
    }
  } finally {
    acting.value = false
  }
}

watch(rescheduleOpen, (open) => {
  if (!open) rescheduleHold.releaseHold()
})

watch([rescheduleDoctorId, rescheduleDate], () => {
  if (rescheduleOpen.value) clearRescheduleSlot()
})

watch(rescheduleHold.expired, (expired) => {
  if (!expired || !newSlotId.value) return
  actionError.value = 'Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ.'
  clearRescheduleSlot({ release: false })
  refreshKey.value += 1
})

onMounted(async () => {
  try {
    await loadDoctors()
  } catch {
    doctors.value = []
  }
  load()
})
</script>
