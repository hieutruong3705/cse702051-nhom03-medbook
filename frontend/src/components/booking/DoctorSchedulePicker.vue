<template>
  <section :aria-labelledby="`${uid}-title`" data-test="schedule-picker">
    <!-- Dải 7 ngày: nhóm radio, chuyển ngày bằng phím mũi tên (một điểm dừng Tab cho cả dải) -->
    <div class="flex items-center gap-1">
      <button
        type="button"
        class="hidden h-9 w-7 shrink-0 items-center justify-center rounded text-slate-600 hover:bg-slate-100 hover:text-primary-700 sm:inline-flex"
        aria-label="Xem các ngày trước"
        tabindex="-1"
        @click="scrollStrip(-1)"
      >
        <i class="fa-solid fa-chevron-left" aria-hidden="true"></i>
      </button>
      <div
        ref="strip"
        class="flex min-w-0 snap-x gap-2 overflow-x-auto scroll-smooth px-0.5 py-1"
        role="radiogroup"
        aria-label="Chọn ngày khám"
        data-test="day-strip"
        @keydown="onStripKey"
      >
        <button
          v-for="(day, index) in days"
          :key="day.value"
          :ref="(el) => (dayButtons[index] = el)"
          type="button"
          role="radio"
          :aria-checked="day.value === selectedDate ? 'true' : 'false'"
          :tabindex="day.value === selectedDate ? 0 : -1"
          :class="dayClass(day)"
          :data-status="dayStatus[day.value] || 'unknown'"
          data-test="day-option"
          @click="selectDay(day.value)"
        >
          <span class="block text-xs font-semibold">{{ day.weekday }}</span>
          <span class="block text-sm font-bold">{{ day.dm }}</span>
          <span v-if="dayStatus[day.value] === 'empty'" class="block text-[11px] font-medium">hết lịch</span>
        </button>
      </div>
      <button
        type="button"
        class="hidden h-9 w-7 shrink-0 items-center justify-center rounded text-slate-600 hover:bg-slate-100 hover:text-primary-700 sm:inline-flex"
        aria-label="Xem các ngày sau"
        tabindex="-1"
        @click="scrollStrip(1)"
      >
        <i class="fa-solid fa-chevron-right" aria-hidden="true"></i>
      </button>
    </div>

    <h3 :id="`${uid}-title`" class="mt-3 flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-slate-800">
      <i class="fa-regular fa-calendar" aria-hidden="true"></i>Lịch khám
    </h3>

    <div v-if="loading" class="mt-2" data-test="slots-skeleton">
      <p class="sr-only" role="status">Đang tải khung giờ…</p>
      <ul class="flex flex-wrap gap-2" aria-hidden="true">
        <li v-for="n in 6" :key="n" class="h-9 w-28 animate-pulse rounded bg-slate-200"></li>
      </ul>
    </div>
    <p v-else-if="error" class="mt-2 text-sm text-red-700" role="alert">{{ error }}</p>
    <template v-else>
      <ul v-if="slots.length" class="mt-3 flex flex-wrap gap-x-2 gap-y-3" :aria-label="`Khung giờ còn trống ngày ${selectedLabel}`">
        <li v-for="(slot, index) in visibleSlots" :key="slot.id">
          <RouterLink
            v-if="canBook"
            class="relative inline-block rounded border border-slate-300 bg-slate-100 px-3 py-1.5 text-sm font-medium text-slate-900 transition hover:-translate-y-0.5 hover:border-primary-600 hover:bg-primary-50 hover:shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600"
            :to="slotLink(slot)"
            :aria-label="`Đặt khung giờ ${formatTime(slot.startTime)} đến ${formatTime(slot.endTime)} ngày ${selectedLabel}${index === 0 ? ', khung giờ sớm nhất' : ''}`"
            data-test="slot-link"
          >
            {{ formatTime(slot.startTime) }} - {{ formatTime(slot.endTime) }}
            <span
              v-if="index === 0"
              class="absolute -right-2 -top-2.5 rounded-full bg-emerald-700 px-1.5 text-[10px] font-semibold leading-4 text-white"
              aria-hidden="true"
              data-test="earliest-tag"
            >Sớm nhất</span>
          </RouterLink>
          <!-- Bác sĩ, lễ tân, quản trị viên chỉ xem lịch: khung giờ là chữ, không dẫn tới trang đặt lịch của bệnh nhân -->
          <span v-else class="inline-block rounded border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm text-slate-700">
            {{ formatTime(slot.startTime) }} - {{ formatTime(slot.endTime) }}
          </span>
        </li>
        <li v-if="slots.length > visibleSlots.length">
          <button type="button" class="px-2 py-1.5 text-sm font-semibold text-primary-700 underline" @click="expanded = true">
            +{{ slots.length - visibleSlots.length }} khung giờ
          </button>
        </li>
      </ul>
      <EmptyState
        v-else
        class="mt-2"
        compact
        illustration="calendar"
        heading-tag="p"
        title="Bác sĩ không có khung giờ trống ngày này."
        :message="nextAvailable ? '' : 'Hãy chọn ngày khác trên dải ngày phía trên.'"
        data-test="no-slots"
      >
        <button
          v-if="nextAvailable"
          type="button"
          class="text-sm font-semibold text-primary-700 underline"
          data-test="jump-next-available"
          @click="selectDay(nextAvailable.value)"
        >
          Xem lịch {{ nextAvailable.label }}
        </button>
      </EmptyState>
      <p v-if="slots.length" class="mt-2 text-xs text-slate-600">
        <template v-if="canBook">Chọn <i class="fa-regular fa-hand-pointer" aria-hidden="true"></i> khung giờ và đặt (phí đặt lịch 0đ, thanh toán tại quầy)</template>
        <template v-else>Chỉ tài khoản bệnh nhân đặt được lịch trực tuyến.</template>
      </p>
    </template>

    <dl v-if="showFacts && facts" class="mt-4 space-y-3 border-t border-slate-200 pt-3 text-sm">
      <div>
        <dt class="font-bold uppercase tracking-wide text-slate-800">Địa chỉ khám</dt>
        <dd class="mt-1 text-slate-700"><span class="font-semibold text-slate-900">{{ facts.name }}</span><br />{{ facts.address }}</dd>
      </div>
      <div v-if="facts.minPrice">
        <dt class="inline font-bold uppercase tracking-wide text-slate-800">Giá khám:</dt>
        <dd class="inline text-slate-700"> từ {{ formatCurrency(facts.minPrice) }}
          <RouterLink class="ml-1 text-primary-700 underline" to="/services">Xem bảng giá</RouterLink>
        </dd>
      </div>
    </dl>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { doctorsApi } from '@/api/doctors'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatTime } from '@/utils/formatters'
import EmptyState from '@/components/ui/EmptyState.vue'
import { bookingLink, upcomingDays } from './bookingLink'
import { loadClinicFacts } from './clinicFacts'

const props = defineProps({
  doctorId: { type: [Number, String], required: true },
  dayCount: { type: Number, default: 7 },
  // Thẻ trong danh sách chỉ hiện một số khung giờ đầu, bấm "+n" để xem hết
  maxSlots: { type: Number, default: 0 },
  showFacts: { type: Boolean, default: true }
})
// availability: báo cho thẻ bác sĩ biết hôm nay còn lịch hay không (huy hiệu "Còn lịch hôm nay")
// earliest: khung giờ sớm nhất của ngày đang xem (null khi trống), cho thanh đặt lịch trên điện thoại
const emit = defineEmits(['availability', 'earliest'])

const uid = `schedule-${props.doctorId}-${Math.random().toString(36).slice(2, 7)}`
const auth = useAuthStore()
const canBook = computed(() => !auth.isAuthenticated || auth.hasRole('PATIENT'))

const days = upcomingDays(props.dayCount)

const selectedDate = ref(days[0].value)
const selectedLabel = computed(() => days.find((day) => day.value === selectedDate.value)?.label ?? '')
const slots = ref([])
const loading = ref(false)
const error = ref('')
const expanded = ref(false)
const facts = ref(null)
// Trạng thái các ngày đã tải: 'available' | 'empty'. Ngày chưa tải không đánh dấu, để không phải gọi API 7 lần cho mỗi bác sĩ
const dayStatus = reactive({})
const strip = ref(null)
const dayButtons = []
let request = 0

function markDay(date, result) {
  dayStatus[date] = result.length ? 'available' : 'empty'
  if (date === days[0].value) emit('availability', { today: result.length > 0 })
}

function dayClass(day) {
  const selected = day.value === selectedDate.value
  const empty = dayStatus[day.value] === 'empty'
  return [
    'min-w-[4.5rem] shrink-0 snap-start rounded-md border px-2 py-1.5 text-center transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-1',
    selected
      ? 'border-primary-700 bg-primary-700 text-white shadow-sm'
      : empty
        ? 'border-dashed border-slate-300 bg-slate-50 text-slate-600'
        : 'border-slate-300 bg-white text-slate-800 hover:border-primary-600 hover:text-primary-700'
  ]
}

// Ngày gần nhất sau ngày đang chọn mà đã biết còn lịch (chỉ trong số ngày đã tải)
const nextAvailable = computed(() => {
  const index = days.findIndex((day) => day.value === selectedDate.value)
  return days.slice(index + 1).find((day) => dayStatus[day.value] === 'available') || null
})

function selectDay(value) {
  selectedDate.value = value
}

const KEY_STEP = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }
function onStripKey(event) {
  const index = days.findIndex((day) => day.value === selectedDate.value)
  let target
  if (event.key in KEY_STEP) target = (index + KEY_STEP[event.key] + days.length) % days.length
  else if (event.key === 'Home') target = 0
  else if (event.key === 'End') target = days.length - 1
  else return
  event.preventDefault()
  selectDay(days[target].value)
  nextTick(() => dayButtons[target]?.focus())
}

function scrollStrip(direction) {
  strip.value?.scrollBy({ left: direction * 160, behavior: 'smooth' })
}

const visibleSlots = computed(() =>
  props.maxSlots && !expanded.value ? slots.value.slice(0, props.maxSlots) : slots.value
)

function slotLink(slot) {
  return bookingLink(props.doctorId, slot, selectedDate.value)
}

async function fetchDay(date) {
  const result = await doctorsApi.slots(props.doctorId, date)
  return Array.isArray(result) ? result : []
}

async function loadSelected() {
  const current = ++request
  loading.value = true
  error.value = ''
  expanded.value = false
  try {
    const date = selectedDate.value
    const result = await fetchDay(date)
    markDay(date, result)
    if (current === request) slots.value = result
  } catch (err) {
    if (current === request) {
      slots.value = []
      error.value = apiErrorMessage(err)
    }
  } finally {
    if (current === request) loading.value = false
  }
}

// Mở ở ngày gần nhất còn khung giờ trống: thử lần lượt từ hôm nay, dừng ở ngày đầu tiên có lịch
async function openFirstAvailable() {
  const current = ++request
  loading.value = true
  try {
    for (const day of days) {
      const result = await fetchDay(day.value).catch(() => null)
      if (current !== request) return
      if (result === null) continue
      markDay(day.value, result)
      if (result.length) {
        if (selectedDate.value !== day.value) skipWatch = true
        selectedDate.value = day.value
        slots.value = result
        return
      }
    }
    slots.value = []
  } finally {
    if (current === request) loading.value = false
  }
}

let skipWatch = false
watch(slots, (list) => {
  emit('earliest', list.length ? { slot: list[0], date: selectedDate.value, label: selectedLabel.value, link: slotLink(list[0]) } : null)
})

watch(selectedDate, () => {
  if (skipWatch) {
    skipWatch = false
    return
  }
  loadSelected()
})

onMounted(async () => {
  openFirstAvailable()
  if (props.showFacts) facts.value = await loadClinicFacts()
})
</script>
