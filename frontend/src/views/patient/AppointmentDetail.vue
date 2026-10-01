<template>
  <PatientLayout>
    <PageHeader eyebrow="Lịch hẹn" :title="appointment ? `Lịch hẹn #${appointment.id}` : 'Chi tiết lịch hẹn'">
      <template #actions>
        <RouterLink to="/patient/appointments" class="text-sm font-semibold text-primary-700 hover:text-primary-900">
          ← Danh sách lịch hẹn
        </RouterLink>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="loading" class="mt-6" label="Đang tải lịch hẹn" />

    <div v-else-if="loadError" class="mt-6 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert" data-test="load-error">
      <p class="font-semibold">{{ loadError }}</p>
      <BaseButton v-if="canRetry" class="mt-3" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>

    <template v-else-if="appointment">
      <p v-if="notice" class="mt-4 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800" role="status" data-test="notice">{{ notice }}</p>

      <section class="mt-6 rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="appointment-info">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 id="appointment-info" class="text-lg font-semibold text-slate-950">Thông tin lịch hẹn</h2>
          <StatusBadge :value="appointment.status" :label="appointmentStatus[appointment.status] || appointment.status" />
        </div>
        <dl class="mt-4 grid gap-4 text-sm sm:grid-cols-2">
          <div><dt class="text-slate-500">Bác sĩ</dt><dd class="font-semibold text-slate-950">{{ appointment.doctorName }}</dd></div>
          <div><dt class="text-slate-500">Chuyên khoa</dt><dd class="font-semibold text-slate-950">{{ appointment.specialtyName || '—' }}</dd></div>
          <div><dt class="text-slate-500">Ngày khám</dt><dd class="font-semibold text-slate-950">{{ formatDate(appointment.date) }}</dd></div>
          <div><dt class="text-slate-500">Giờ khám</dt><dd class="font-semibold text-slate-950">{{ formatTime(appointment.startTime) }} – {{ formatTime(appointment.endTime) }}</dd></div>
          <div><dt class="text-slate-500">Dịch vụ</dt><dd class="font-semibold text-slate-950">{{ appointment.serviceName || '—' }}</dd></div>
          <div><dt class="text-slate-500">Ngày tạo</dt><dd class="font-semibold text-slate-950">{{ formatDateTime(appointment.createdAt) }}</dd></div>
          <div v-if="appointment.notes" class="sm:col-span-2"><dt class="text-slate-500">Ghi chú của bạn</dt><dd class="whitespace-pre-line text-slate-900">{{ appointment.notes }}</dd></div>
          <div v-if="appointment.status === 'CANCELLED'" class="sm:col-span-2" data-test="cancel-info">
            <dt class="text-slate-500">Đã hủy lúc {{ formatDateTime(appointment.cancelledAt) }}</dt>
            <dd class="text-slate-900">{{ appointment.cancelReason || 'Không có lý do được ghi nhận.' }}</dd>
          </div>
        </dl>

        <div v-if="canChange" class="mt-6 flex flex-wrap gap-3 border-t border-slate-100 pt-4">
          <BaseButton variant="secondary" icon="fa-regular fa-calendar" @click="openReschedule">Đổi lịch</BaseButton>
          <BaseButton variant="danger" icon="fa-solid fa-ban" @click="openCancel">Hủy lịch</BaseButton>
        </div>
        <p v-else-if="appointment.status === 'BOOKED'" class="mt-4 text-sm text-slate-500">
          Lịch hẹn này đã qua giờ khám nên không thể hủy hoặc đổi.
        </p>
      </section>
    </template>

    <!-- Hủy lịch -->
    <FormModal v-model="cancelOpen" title="Hủy lịch hẹn" description="Khung giờ sẽ được giải phóng cho bệnh nhân khác. Thao tác này không thể hoàn tác.">
      <BaseTextarea id="cancel-reason" v-model="cancelReason" label="Lý do hủy (không bắt buộc)" :rows="3" :error="cancelReason.length > 500 ? 'Lý do tối đa 500 ký tự' : ''" />
      <p v-if="actionError" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="cancel-error">{{ actionError }}</p>
      <template #footer>
        <BaseButton variant="secondary" :disabled="acting" @click="cancelOpen = false">Giữ lịch</BaseButton>
        <BaseButton variant="danger" :loading="acting" :disabled="cancelReason.length > 500" @click="confirmCancel">Xác nhận hủy</BaseButton>
      </template>
    </FormModal>

    <!-- Đổi lịch -->
    <FormModal v-model="rescheduleOpen" title="Đổi lịch hẹn" description="Chọn khung giờ mới. Lịch hiện tại chỉ được thay khi khung giờ mới đặt thành công.">
      <div class="space-y-4">
        <BaseSelect id="reschedule-doctor" :model-value="rescheduleDoctorId" label="Bác sĩ" :options="doctorOptions" @update:model-value="onDoctorChange" />
        <BaseDatePicker id="reschedule-date" v-model="rescheduleDate" label="Ngày khám mới" :error="isPastDate(rescheduleDate) ? 'Ngày khám phải từ hôm nay trở đi' : ''" />
        <SlotPicker
          :doctor-id="rescheduleDoctorId || null"
          :date="rescheduleDate"
          :model-value="newSlotId"
          :refresh-key="refreshKey"
          @update:model-value="(value) => (newSlotId = value)"
        />
        <BaseTextarea id="reschedule-reason" v-model="rescheduleReason" label="Lý do đổi lịch (không bắt buộc)" :rows="2" :error="rescheduleReason.length > 500 ? 'Lý do tối đa 500 ký tự' : ''" />
        <p v-if="actionError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="reschedule-error">{{ actionError }}</p>
      </div>
      <template #footer>
        <BaseButton variant="secondary" :disabled="acting" @click="rescheduleOpen = false">Đóng</BaseButton>
        <BaseButton :loading="acting" :disabled="!newSlotId || rescheduleReason.length > 500" @click="confirmReschedule">Xác nhận đổi lịch</BaseButton>
      </template>
    </FormModal>
  </PatientLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  BaseButton,
  BaseDatePicker,
  BaseSelect,
  BaseTextarea,
  FormModal,
  LoadingSpinner,
  PageHeader,
  StatusBadge
} from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { appointmentsApi } from '@/api/appointments'
import { doctorsApi } from '@/api/doctors'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatDateTime, formatTime } from '@/utils/formatters'
import { appointmentStatus } from '@/utils/statusLabels'
import SlotPicker from './booking/SlotPicker.vue'
import { doctorName, isPastDate, normalizeList } from './booking/bookingUtils'

const route = useRoute()
const toast = useToast()

const appointment = ref(null)
const loading = ref(false)
const loadError = ref('')
const canRetry = ref(true)
const notice = ref('')

const acting = ref(false)
const actionError = ref('')

const cancelOpen = ref(false)
const cancelReason = ref('')

const rescheduleOpen = ref(false)
const rescheduleDoctorId = ref('')
const rescheduleDate = ref('')
const rescheduleReason = ref('')
const newSlotId = ref(null)
const refreshKey = ref(0)
const doctors = ref([])

const appointmentId = computed(() => route.params.id)

/** Còn hủy/đổi được khi BOOKED và chưa đến giờ khám; thời hạn tối thiểu cụ thể do server quyết định (409). */
const canChange = computed(() => {
  const item = appointment.value
  if (!item || item.status !== 'BOOKED') return false
  const startsAt = new Date(`${item.date}T${item.startTime}`)
  return Number.isNaN(startsAt.valueOf()) || startsAt.getTime() > Date.now()
})

const doctorOptions = computed(() => doctors.value.map((item) => ({ label: doctorName(item), value: item.id })))

async function load() {
  loading.value = true
  loadError.value = ''
  canRetry.value = true
  try {
    appointment.value = await appointmentsApi.get(appointmentId.value)
  } catch (err) {
    appointment.value = null
    if (err?.status === 403) {
      loadError.value = 'Bạn không có quyền xem lịch hẹn này.'
      canRetry.value = false
    } else if (err?.status === 404) {
      loadError.value = 'Không tìm thấy lịch hẹn.'
      canRetry.value = false
    } else {
      loadError.value = apiErrorMessage(err)
    }
  } finally {
    loading.value = false
  }
}

function openCancel() {
  actionError.value = ''
  cancelReason.value = ''
  cancelOpen.value = true
}

async function confirmCancel() {
  if (acting.value) return
  acting.value = true
  actionError.value = ''
  try {
    appointment.value = await appointmentsApi.cancel(appointmentId.value, { reason: cancelReason.value.trim() || undefined })
    cancelOpen.value = false
    notice.value = 'Đã hủy lịch hẹn. Khung giờ đã được giải phóng.'
    toast.success('Đã hủy lịch hẹn')
  } catch (err) {
    actionError.value = apiErrorMessage(err)
    // 409: trạng thái đã đổi hoặc quá hạn hủy → tải lại để hiển thị đúng thực tế
    if (err?.status === 409) await reloadQuietly()
  } finally {
    acting.value = false
  }
}

async function openReschedule() {
  actionError.value = ''
  rescheduleReason.value = ''
  rescheduleDate.value = ''
  newSlotId.value = null
  rescheduleDoctorId.value = appointment.value?.doctorId ?? ''
  rescheduleOpen.value = true
  if (!doctors.value.length) {
    try {
      doctors.value = normalizeList(await doctorsApi.list({ size: 100 }))
    } catch (err) {
      actionError.value = apiErrorMessage(err)
    }
  }
}

function onDoctorChange(value) {
  rescheduleDoctorId.value = value === '' ? '' : Number(value)
  newSlotId.value = null
}

async function confirmReschedule() {
  if (acting.value || !newSlotId.value) return
  acting.value = true
  actionError.value = ''
  try {
    appointment.value = await appointmentsApi.reschedule(appointmentId.value, {
      newSlotId: newSlotId.value,
      reason: rescheduleReason.value.trim() || undefined
    })
    rescheduleOpen.value = false
    notice.value = 'Đã đổi lịch hẹn sang khung giờ mới.'
    toast.success('Đã đổi lịch hẹn')
  } catch (err) {
    actionError.value = apiErrorMessage(err)
    if (err?.status === 409 || err?.status === 404) {
      // Khung giờ mới vừa bị người khác đặt: lịch cũ giữ nguyên; tải lại danh sách giờ, bỏ lựa chọn cũ
      newSlotId.value = null
      refreshKey.value += 1
      await reloadQuietly()
    }
  } finally {
    acting.value = false
  }
}

async function reloadQuietly() {
  try {
    appointment.value = await appointmentsApi.get(appointmentId.value)
  } catch {
    // giữ dữ liệu đang hiển thị; thông báo lỗi chính đã có trong hộp thoại
  }
}

onMounted(load)
</script>
