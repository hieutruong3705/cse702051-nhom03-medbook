<template>
  <div class="mt-4" data-test="done">
    <div class="flex flex-col items-center text-center">
      <!-- Dấu tích vẽ dần một lần (tắt khi bật "giảm chuyển động", xem style.css) -->
      <svg class="h-20 w-20" viewBox="0 0 64 64" aria-hidden="true" focusable="false" data-test="success-check">
        <circle class="draw-circle" cx="32" cy="32" r="25" fill="#ecfdf5" stroke="#047857" stroke-width="4" />
        <path class="draw-check" d="M20 33 L28 41 L45 24" fill="none" stroke="#047857" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <p class="mt-3 text-lg font-bold text-emerald-800" role="status">
        Đặt lịch thành công. Mã lịch hẹn: <strong>#{{ appointment?.id }}</strong>.
      </p>
      <p class="mt-1 text-sm text-slate-600">Khung giờ đã được giữ cho bạn. Hãy có mặt trước giờ hẹn khoảng 15 phút.</p>
    </div>

    <dl class="mx-auto mt-5 grid max-w-xl gap-3 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm sm:grid-cols-2" data-test="success-summary">
      <div><dt class="text-slate-600">Bác sĩ</dt><dd class="font-semibold text-slate-900">{{ appointment?.doctorName || '-' }}</dd></div>
      <div><dt class="text-slate-600">Dịch vụ</dt><dd class="font-semibold text-slate-900">{{ serviceName || '-' }}</dd></div>
      <div><dt class="text-slate-600">Ngày khám</dt><dd class="font-semibold text-slate-900">{{ formatDate(appointment?.date) }}</dd></div>
      <div><dt class="text-slate-600">Giờ khám</dt><dd class="font-semibold text-slate-900">{{ formatTime(appointment?.startTime) }}–{{ formatTime(appointment?.endTime) }}</dd></div>
      <div v-if="facts?.address" class="sm:col-span-2"><dt class="text-slate-600">Địa chỉ</dt><dd class="text-slate-900">{{ facts.name }}, {{ facts.address }}</dd></div>
    </dl>

    <div class="mt-5 flex flex-wrap justify-center gap-3">
      <RouterLink class="rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700" :to="`/patient/appointments/${appointment?.id}`">
        Xem chi tiết lịch hẹn
      </RouterLink>
      <button
        type="button"
        class="inline-flex items-center gap-2 rounded-md border border-primary-600 px-4 py-2 text-sm font-semibold text-primary-700 hover:bg-primary-50"
        data-test="add-to-calendar"
        @click="addToCalendar"
      >
        <i class="fa-regular fa-calendar-plus" aria-hidden="true"></i>Thêm vào lịch (.ics)
      </button>
      <BaseButton variant="secondary" @click="$emit('restart')">Đặt thêm lịch khác</BaseButton>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { BaseButton } from '@/components/ui'
import { loadClinicFacts } from '@/components/booking/clinicFacts'
import { formatDate, formatTime } from '@/utils/formatters'
import { buildIcs, downloadIcs } from '@/utils/ics'

const props = defineProps({
  appointment: { type: Object, default: null },
  // Tên dịch vụ đã chọn ở bước 1 (dùng khi phản hồi của máy chủ không có serviceName)
  service: { type: Object, default: null }
})
defineEmits(['restart'])

const facts = ref(null)
const serviceName = computed(() => props.appointment?.serviceName || props.service?.name || '')

onMounted(async () => {
  facts.value = await loadClinicFacts().catch(() => null)
})

/** Nội dung tệp .ics của lịch hẹn vừa đặt (tách riêng để kiểm thử). */
function icsContent() {
  const a = props.appointment
  const location = facts.value ? `${facts.value.name}, ${facts.value.address}` : ''
  return buildIcs({
    id: a.id,
    title: `${serviceName.value || 'Lịch khám'} với ${a.doctorName} (MedBook)`,
    description: `Mã lịch hẹn #${a.id}. Thanh toán tại quầy sau khi khám. Đổi hoặc hủy lịch trước giờ khám ít nhất 2 giờ.`,
    location,
    date: a.date,
    startTime: a.startTime,
    endTime: a.endTime
  })
}

function addToCalendar() {
  if (!props.appointment) return
  downloadIcs(`lich-kham-medbook-${props.appointment.id}.ics`, icsContent())
}

defineExpose({ icsContent })
</script>
