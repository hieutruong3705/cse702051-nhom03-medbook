<template>
  <PatientLayout>
    <PageHeader
      eyebrow="Khu bệnh nhân"
      title="Đặt lịch khám"
      description="Chọn bác sĩ, ngày và khung giờ phù hợp. Danh tính của bạn được lấy từ tài khoản đang đăng nhập."
    />

    <nav class="mt-6" aria-label="Các bước đặt lịch">
      <ol class="grid grid-cols-5 gap-2 text-center text-xs sm:text-sm">
        <li v-for="item in BOOKING_STEPS" :key="item.id">
          <button
            type="button"
            class="w-full rounded-md border px-1 py-2 font-semibold transition disabled:cursor-not-allowed"
            :class="stepClass(item.id)"
            :aria-current="flow.step.value === item.id ? 'step' : undefined"
            :disabled="item.id > flow.furthest.value || flow.step.value === DONE_STEP || flow.submitting.value"
            @click="flow.goTo(item.id)"
          >
            <span class="block">{{ item.id }}</span>
            <span class="block">{{ item.label }}</span>
          </button>
        </li>
      </ol>
    </nav>

    <section class="mt-6 rounded-md border border-slate-200 bg-white p-4 sm:p-6" :aria-labelledby="'booking-step-title'">
      <h2 id="booking-step-title" ref="heading" tabindex="-1" class="text-lg font-semibold text-slate-950 focus:outline-none">
        {{ stepTitle }}
      </h2>

      <p v-if="flow.conflictMessage.value" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900" role="alert" data-test="conflict">
        {{ flow.conflictMessage.value }}
      </p>

      <!-- Bước 1: bác sĩ -->
      <div v-if="flow.step.value === 1" class="mt-4 space-y-4">
        <BaseInput id="booking-doctor-search" v-model="keyword" label="Tìm theo tên bác sĩ hoặc chuyên khoa" placeholder="Ví dụ: Nội tổng quát" />
        <LoadingSpinner v-if="loadingDoctors" label="Đang tải danh sách bác sĩ" />
        <p v-else-if="doctorsError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ doctorsError }}</p>
        <EmptyState v-else-if="!filteredDoctors.length" icon="fa-solid fa-user-doctor" title="Không tìm thấy bác sĩ phù hợp" message="Hãy thử từ khóa khác." />
        <ul v-else class="grid gap-3 sm:grid-cols-2" aria-label="Danh sách bác sĩ">
          <li v-for="item in filteredDoctors" :key="item.id">
            <button
              type="button"
              class="w-full rounded-md border p-3 text-left transition focus:outline-none focus:ring-2 focus:ring-primary-600/40"
              :class="flow.doctor.value?.id === item.id ? 'border-primary-600 bg-primary-50' : 'border-slate-300 hover:border-primary-600'"
              :aria-pressed="flow.doctor.value?.id === item.id"
              @click="flow.selectDoctor(item)"
            >
              <span class="block font-semibold text-slate-950">{{ doctorName(item) }}</span>
              <span class="block text-sm text-slate-600">{{ doctorSpecialty(item) || 'Chưa cập nhật chuyên khoa' }}</span>
            </button>
          </li>
        </ul>
      </div>

      <!-- Bước 2: ngày -->
      <div v-else-if="flow.step.value === 2" class="mt-4 max-w-xs">
        <BaseDatePicker
          id="booking-date"
          :model-value="flow.date.value"
          label="Ngày khám"
          required
          :error="dateError"
          @update:model-value="flow.setDate"
        />
      </div>

      <!-- Bước 3: khung giờ -->
      <div v-else-if="flow.step.value === 3" class="mt-4">
        <p class="mb-3 text-sm text-slate-600">
          {{ doctorName(flow.doctor.value) }} · {{ formatDate(flow.date.value) }}
        </p>
        <SlotPicker
          :doctor-id="flow.doctor.value?.id"
          :date="flow.date.value"
          :model-value="flow.slot.value?.id ?? null"
          :refresh-key="flow.slotsRefreshKey.value"
          @select="flow.selectSlot"
          @update:model-value="onSlotCleared"
        />
      </div>

      <!-- Bước 4: dịch vụ -->
      <div v-else-if="flow.step.value === 4" class="mt-4 space-y-4">
        <LoadingSpinner v-if="loadingServices" label="Đang tải dịch vụ" />
        <p v-else-if="servicesError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ servicesError }}</p>
        <template v-else>
          <BaseSelect
            id="booking-service"
            :model-value="flow.service.value?.id ?? ''"
            label="Dịch vụ khám"
            required
            placeholder="Chọn dịch vụ"
            :options="serviceOptions"
            :error="flow.fieldErrors.serviceId"
            @update:model-value="selectService"
          />
          <p v-if="flow.service.value" class="text-sm text-slate-600">
            Giá tham khảo: <strong>{{ formatCurrency(flow.service.value.price) }}</strong>
            <span v-if="flow.service.value.durationMinutes"> · khoảng {{ flow.service.value.durationMinutes }} phút</span>
          </p>
          <BaseTextarea
            id="booking-notes"
            v-model="flow.notes.value"
            label="Triệu chứng / ghi chú cho bác sĩ"
            :rows="4"
            :error="flow.notesError.value || flow.fieldErrors.notes"
            hint="Không bắt buộc. Tối đa 2000 ký tự."
          />
        </template>
      </div>

      <!-- Bước 5: xác nhận -->
      <div v-else-if="flow.step.value === 5" class="mt-4">
        <dl class="grid gap-3 text-sm sm:grid-cols-2" data-test="summary">
          <div><dt class="text-slate-500">Bác sĩ</dt><dd class="font-semibold text-slate-950">{{ doctorName(flow.doctor.value) }}</dd></div>
          <div><dt class="text-slate-500">Chuyên khoa</dt><dd class="font-semibold text-slate-950">{{ doctorSpecialty(flow.doctor.value) || '—' }}</dd></div>
          <div><dt class="text-slate-500">Ngày khám</dt><dd class="font-semibold text-slate-950">{{ formatDate(flow.date.value) }}</dd></div>
          <div><dt class="text-slate-500">Giờ khám</dt><dd class="font-semibold text-slate-950">{{ flow.slot.value ? slotLabel(flow.slot.value) : '—' }}</dd></div>
          <div><dt class="text-slate-500">Dịch vụ</dt><dd class="font-semibold text-slate-950">{{ flow.service.value?.name }}</dd></div>
          <div><dt class="text-slate-500">Giá tham khảo</dt><dd class="font-semibold text-slate-950">{{ formatCurrency(flow.service.value?.price) }}</dd></div>
          <div v-if="flow.notes.value.trim()" class="sm:col-span-2"><dt class="text-slate-500">Ghi chú</dt><dd class="whitespace-pre-line text-slate-900">{{ flow.notes.value.trim() }}</dd></div>
        </dl>
        <p v-if="flow.submitError.value" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ flow.submitError.value }}</p>
      </div>

      <!-- Hoàn tất -->
      <div v-else class="mt-4" data-test="done">
        <p class="rounded-md bg-emerald-50 px-3 py-3 text-sm text-emerald-800" role="status">
          Đặt lịch thành công. Mã lịch hẹn: <strong>#{{ flow.confirmed.value?.id }}</strong>.
        </p>
        <p class="mt-3 text-sm text-slate-600">
          {{ flow.confirmed.value?.doctorName }} · {{ formatDate(flow.confirmed.value?.date) }} ·
          {{ formatTime(flow.confirmed.value?.startTime) }}–{{ formatTime(flow.confirmed.value?.endTime) }}
        </p>
        <div class="mt-4 flex flex-wrap gap-3">
          <RouterLink class="rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700" :to="`/patient/appointments/${flow.confirmed.value?.id}`">
            Xem chi tiết lịch hẹn
          </RouterLink>
          <BaseButton variant="secondary" @click="flow.restart">Đặt thêm lịch khác</BaseButton>
        </div>
      </div>

      <!-- Điều hướng bước -->
      <div v-if="flow.step.value <= 5" class="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
        <BaseButton variant="secondary" :disabled="flow.step.value === 1 || flow.submitting.value" @click="flow.back">Quay lại</BaseButton>
        <div class="flex items-center gap-3">
          <p v-if="flow.step.value < 5 && flow.currentProblem.value" class="text-sm text-slate-500">{{ flow.currentProblem.value }}</p>
          <BaseButton v-if="flow.step.value < 5" :disabled="!flow.canContinue.value" @click="flow.next">Tiếp tục</BaseButton>
          <BaseButton v-else :loading="flow.submitting.value" icon="fa-solid fa-check" @click="flow.submit">Xác nhận đặt lịch</BaseButton>
        </div>
      </div>
    </section>
  </PatientLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  BaseButton,
  BaseDatePicker,
  BaseInput,
  BaseSelect,
  BaseTextarea,
  EmptyState,
  LoadingSpinner,
  PageHeader
} from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatDate, formatTime } from '@/utils/formatters'
import SlotPicker from './SlotPicker.vue'
import { doctorName, doctorSpecialty, isPastDate, normalizeList, slotLabel } from './bookingUtils'
import { BOOKING_STEPS, DONE_STEP, useBookingFlow } from './useBookingFlow'

const route = useRoute()
const flow = useBookingFlow()

const heading = ref(null)
const keyword = ref('')
const doctors = ref([])
const loadingDoctors = ref(false)
const doctorsError = ref('')
const services = ref([])
const loadingServices = ref(false)
const servicesError = ref('')

const stepTitles = {
  1: 'Bước 1: Chọn bác sĩ',
  2: 'Bước 2: Chọn ngày khám',
  3: 'Bước 3: Chọn khung giờ',
  4: 'Bước 4: Chọn dịch vụ và ghi chú',
  5: 'Bước 5: Xác nhận thông tin',
  [DONE_STEP]: 'Hoàn tất'
}
const stepTitle = computed(() => stepTitles[flow.step.value])

const filteredDoctors = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  if (!term) return doctors.value
  return doctors.value.filter((item) => `${doctorName(item)} ${doctorSpecialty(item)}`.toLowerCase().includes(term))
})

const serviceOptions = computed(() => services.value.map((item) => ({ label: item.name, value: item.id })))
const dateError = computed(() => (isPastDate(flow.date.value) ? 'Ngày khám phải từ hôm nay trở đi' : ''))

function stepClass(id) {
  if (flow.step.value === id) return 'border-primary-600 bg-primary-600 text-white'
  if (id < flow.step.value || flow.step.value === DONE_STEP) return 'border-primary-200 bg-primary-50 text-primary-800'
  return 'border-slate-200 bg-white text-slate-500'
}

function selectService(value) {
  flow.service.value = services.value.find((item) => String(item.id) === String(value)) ?? null
}

// SlotPicker báo slot đã chọn không còn trong danh sách mới tải → bỏ lựa chọn
function onSlotCleared(value) {
  if (value === null) flow.slot.value = null
}

async function loadDoctors() {
  loadingDoctors.value = true
  doctorsError.value = ''
  try {
    doctors.value = normalizeList(await doctorsApi.list({ size: 100 }))
    await preselectDoctor()
  } catch (err) {
    doctorsError.value = apiErrorMessage(err)
  } finally {
    loadingDoctors.value = false
  }
}

/** `/patient/booking?doctorId=7` chọn sẵn bác sĩ và chuyển thẳng sang bước chọn ngày. */
async function preselectDoctor() {
  const raw = Array.isArray(route.query.doctorId) ? route.query.doctorId[0] : route.query.doctorId
  if (!raw) return
  let found = doctors.value.find((item) => String(item.id) === String(raw))
  if (!found) {
    try {
      found = await doctorsApi.get(raw)
    } catch {
      doctorsError.value = 'Không tìm thấy bác sĩ được chọn. Vui lòng chọn bác sĩ trong danh sách.'
      return
    }
  }
  flow.selectDoctor(found)
  flow.next()
}

async function loadServices() {
  loadingServices.value = true
  servicesError.value = ''
  try {
    services.value = normalizeList(await catalogApi.services.list({ status: 'ACTIVE', size: 100 }))
  } catch (err) {
    servicesError.value = apiErrorMessage(err)
  } finally {
    loadingServices.value = false
  }
}

// Chuyển bước thì đưa focus về tiêu đề để người dùng bàn phím/trình đọc màn hình biết nội dung đã đổi
watch(
  () => flow.step.value,
  async () => {
    await nextTick()
    heading.value?.focus()
  }
)

onMounted(() => {
  loadDoctors()
  loadServices()
})
</script>
