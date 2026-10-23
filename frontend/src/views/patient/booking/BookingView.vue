<template>
  <PatientLayout>
    <PageHeader
      eyebrow="Khu bệnh nhân"
      title="Đặt lịch khám"
      description="Chọn dịch vụ, bác sĩ, ngày và khung giờ phù hợp. Danh tính của bạn được lấy từ tài khoản đang đăng nhập."
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

      <!-- Đồng hồ giữ chỗ: phần nhìn thấy đổi từng giây; phần cho trình đọc màn hình chỉ đổi theo phút để không đọc liên tục -->
      <div
        v-if="flow.hold.value && flow.step.value >= SLOT_STEP && flow.step.value <= CONFIRM_STEP"
        class="mt-3 flex items-center gap-2 rounded-md border border-primary-200 bg-primary-50 px-3 py-2 text-sm text-primary-900"
        data-test="hold-countdown"
      >
        <i class="fa-regular fa-clock" aria-hidden="true"></i>
        <span aria-hidden="true">Đang giữ chỗ, còn <strong>{{ formatHoldCountdown(flow.remainingSeconds.value) }}</strong></span>
        <span class="sr-only" aria-live="polite">{{ holdAnnouncement }}</span>
      </div>

      <!-- Bước 1: dịch vụ và gợi ý giờ sớm nhất -->
      <div v-if="flow.step.value === 1" class="mt-4 space-y-4">
        <LoadingSpinner v-if="loadingServices" label="Đang tải dịch vụ" />
        <div v-else-if="servicesError" class="rounded-md bg-red-50 px-3 py-3 text-sm text-red-700" role="alert">
          <p>{{ servicesError }}</p>
          <BaseButton class="mt-2" variant="secondary" @click="loadServices">Thử lại</BaseButton>
        </div>
        <template v-else>
          <div class="max-w-md">
            <BaseSelect
              id="booking-service"
              :model-value="flow.service.value?.id ?? ''"
              label="Dịch vụ khám"
              required
              placeholder="Chọn dịch vụ"
              :options="serviceOptions"
              :error="flow.fieldErrors.serviceId"
              hint="Danh sách khung giờ trống phụ thuộc thời lượng của dịch vụ."
              @update:model-value="selectService"
            />
          </div>
          <p v-if="flow.service.value" class="text-sm text-slate-600">
            Giá tham khảo: <strong>{{ formatCurrency(flow.service.value.price) }}</strong>
            <span v-if="flow.service.value.durationMinutes"> · khoảng {{ flow.service.value.durationMinutes }} phút</span>
          </p>

          <section v-if="flow.service.value" class="rounded-md border border-slate-200 p-3 sm:p-4" aria-labelledby="earliest-title" data-test="earliest">
            <h3 id="earliest-title" class="font-semibold text-slate-950">Giờ sớm nhất còn trống</h3>
            <p class="mt-1 text-sm text-slate-600">Chọn một dòng để tới thẳng bước xác nhận, hoặc bấm Tiếp tục để tự chọn bác sĩ và ngày.</p>
            <div class="mt-3 max-w-md">
              <BaseSelect
                id="booking-earliest-specialty"
                v-model="earliestSpecialty"
                label="Chuyên khoa"
                placeholder="Tất cả chuyên khoa"
                :options="specialtyOptions"
              />
            </div>
            <LoadingSpinner v-if="loadingEarliest" class="mt-3" label="Đang tìm giờ sớm nhất" />
            <div v-else-if="earliestError" class="mt-3 rounded-md bg-red-50 px-3 py-3 text-sm text-red-700" role="alert">
              <p>{{ earliestError }}</p>
              <BaseButton class="mt-2" variant="secondary" @click="loadEarliest">Thử lại</BaseButton>
            </div>
            <p v-else-if="!earliest.length" class="mt-3 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">
              Chưa có khung giờ trống phù hợp trong 30 ngày tới. Hãy thử chuyên khoa khác hoặc dịch vụ khác.
            </p>
            <ul v-else class="mt-3 grid gap-2 sm:grid-cols-2" aria-label="Giờ sớm nhất còn trống">
              <li v-for="item in earliest" :key="item.slotId">
                <button
                  type="button"
                  class="w-full rounded-md border border-slate-300 bg-white p-3 text-left transition hover:border-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-600/40 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="choosingEarliest"
                  @click="pickEarliest(item)"
                >
                  <span class="block font-semibold text-slate-950">
                    {{ formatDate(item.date) }} · {{ formatTime(item.startTime) }} – {{ formatTime(item.endTime) }}
                  </span>
                  <span class="block text-sm text-slate-600">{{ item.doctorName }}<template v-if="item.specialtyName"> · {{ item.specialtyName }}</template></span>
                </button>
              </li>
            </ul>
          </section>
        </template>
      </div>

      <!-- Bước 2: bác sĩ -->
      <div v-else-if="flow.step.value === 2" class="mt-4 space-y-4">
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

      <!-- Bước 3: ngày -->
      <div v-else-if="flow.step.value === 3" class="mt-4 max-w-xs">
        <BaseDatePicker
          id="booking-date"
          :model-value="flow.date.value"
          label="Ngày khám"
          required
          :error="dateError"
          @update:model-value="flow.setDate"
        />
      </div>

      <!-- Bước 4: khung giờ (chọn là giữ chỗ) -->
      <div v-else-if="flow.step.value === 4" class="mt-4">
        <p class="mb-3 text-sm text-slate-600">
          {{ doctorName(flow.doctor.value) }} · {{ formatDate(flow.date.value) }} · {{ flow.service.value?.name }}
        </p>
        <SlotPicker
          :doctor-id="flow.doctor.value?.id"
          :date="flow.date.value"
          :service-id="flow.service.value?.id ?? null"
          :model-value="flow.slot.value?.id ?? null"
          :refresh-key="flow.slotsRefreshKey.value"
          @select="flow.selectSlot"
          @update:model-value="onSlotCleared"
        />
        <p class="mt-3 text-sm text-slate-500">Khung giờ bạn chọn được giữ riêng cho bạn trong thời gian có hạn để hoàn tất đặt lịch.</p>
      </div>

      <!-- Bước 5: ghi chú và xác nhận -->
      <div v-else-if="flow.step.value === 5" class="mt-4">
        <dl class="grid gap-3 text-sm sm:grid-cols-2" data-test="summary">
          <div><dt class="text-slate-500">Dịch vụ</dt><dd class="font-semibold text-slate-950">{{ flow.service.value?.name }}</dd></div>
          <div><dt class="text-slate-500">Giá tham khảo</dt><dd class="font-semibold text-slate-950">{{ formatCurrency(flow.service.value?.price) }}</dd></div>
          <div><dt class="text-slate-500">Bác sĩ</dt><dd class="font-semibold text-slate-950">{{ doctorName(flow.doctor.value) }}</dd></div>
          <div><dt class="text-slate-500">Chuyên khoa</dt><dd class="font-semibold text-slate-950">{{ doctorSpecialty(flow.doctor.value) || '—' }}</dd></div>
          <div><dt class="text-slate-500">Ngày khám</dt><dd class="font-semibold text-slate-950">{{ formatDate(flow.date.value) }}</dd></div>
          <div><dt class="text-slate-500">Giờ khám</dt><dd class="font-semibold text-slate-950">{{ flow.slot.value ? slotLabel(flow.slot.value) : '—' }}</dd></div>
        </dl>
        <div class="mt-4">
          <BaseTextarea
            id="booking-notes"
            v-model="flow.notes.value"
            label="Triệu chứng / ghi chú cho bác sĩ"
            :rows="4"
            :error="flow.notesError.value || flow.fieldErrors.notes"
            hint="Không bắt buộc. Tối đa 2000 ký tự."
          />
        </div>
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
          <BaseButton v-if="flow.step.value < 5" :disabled="!flow.canContinue.value" @click="onNext">Tiếp tục</BaseButton>
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
import { slotsApi } from '@/api/slots'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatDate, formatTime } from '@/utils/formatters'
import SlotPicker from './SlotPicker.vue'
import { doctorName, doctorSpecialty, isPastDate, normalizeList, slotLabel } from './bookingUtils'
import { BOOKING_STEPS, CONFIRM_STEP, DONE_STEP, SLOT_STEP, useBookingFlow } from './useBookingFlow'
import { formatHoldCountdown } from './useSlotHold'

const EARLIEST_LIMIT = 5

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
const specialties = ref([])
const earliest = ref([])
const earliestSpecialty = ref('')
const loadingEarliest = ref(false)
const earliestError = ref('')
const choosingEarliest = ref(false)
// Bác sĩ được chọn sẵn qua `?doctorId=`: chọn xong dịch vụ thì bỏ qua bước chọn bác sĩ một lần
let skipDoctorStep = false
let earliestRequest = 0

const stepTitles = {
  1: 'Bước 1: Chọn dịch vụ',
  2: 'Bước 2: Chọn bác sĩ',
  3: 'Bước 3: Chọn ngày khám',
  4: 'Bước 4: Chọn khung giờ',
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
const specialtyOptions = computed(() => specialties.value.map((item) => ({ label: item.name, value: item.id })))
const dateError = computed(() => (isPastDate(flow.date.value) ? 'Ngày khám phải từ hôm nay trở đi' : ''))

// Thông báo cho trình đọc màn hình: chỉ đổi khi số phút đổi, và đổi dày hơn ở nửa phút cuối
const holdAnnouncement = computed(() => {
  const seconds = flow.remainingSeconds.value
  if (!flow.hold.value) return ''
  if (seconds > 30) return `Đang giữ chỗ, còn khoảng ${Math.ceil(seconds / 60)} phút`
  return seconds > 10 ? 'Đang giữ chỗ, còn chưa đầy 30 giây' : 'Đang giữ chỗ, còn chưa đầy 10 giây'
})

function stepClass(id) {
  if (flow.step.value === id) return 'border-primary-600 bg-primary-600 text-white'
  if (id < flow.step.value || flow.step.value === DONE_STEP) return 'border-primary-200 bg-primary-50 text-primary-800'
  return 'border-slate-200 bg-white text-slate-500'
}

function selectService(value) {
  flow.selectService(services.value.find((item) => String(item.id) === String(value)) ?? null)
}

function onNext() {
  const leavingServiceStep = flow.step.value === 1
  if (!flow.next()) return
  if (leavingServiceStep && skipDoctorStep && flow.doctor.value) {
    skipDoctorStep = false
    flow.next()
  }
}

// SlotPicker báo slot đã chọn không còn trong danh sách mới tải → bỏ lựa chọn và nhả bản giữ chỗ (nếu còn)
function onSlotCleared(value) {
  if (value === null) flow.clearSlot()
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

/** `/patient/booking?doctorId=7` chọn sẵn bác sĩ; sau khi chọn dịch vụ, luồng đi thẳng sang bước chọn ngày. */
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
  skipDoctorStep = true
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

// Bộ lọc chuyên khoa của khối "giờ sớm nhất" chỉ là tiện ích: không tải được thì vẫn đặt lịch bình thường
async function loadSpecialties() {
  try {
    specialties.value = normalizeList(await catalogApi.specialties.list({ status: 'ACTIVE', size: 100 }))
  } catch {
    specialties.value = []
  }
}

async function loadEarliest() {
  earliest.value = []
  earliestError.value = ''
  if (!flow.service.value) return
  const current = ++earliestRequest
  loadingEarliest.value = true
  try {
    const response = await slotsApi.earliest({
      serviceId: flow.service.value.id,
      specialtyId: earliestSpecialty.value || undefined,
      limit: EARLIEST_LIMIT
    })
    if (current === earliestRequest) earliest.value = normalizeList(response).slice(0, EARLIEST_LIMIT)
  } catch (err) {
    if (current === earliestRequest) earliestError.value = apiErrorMessage(err)
  } finally {
    if (current === earliestRequest) loadingEarliest.value = false
  }
}

/** Chọn một dòng "giờ sớm nhất": điền bác sĩ, ngày, khung giờ, giữ chỗ rồi tới bước xác nhận. */
async function pickEarliest(item) {
  if (choosingEarliest.value) return
  choosingEarliest.value = true
  try {
    const doctor = doctors.value.find((candidate) => candidate.id === item.doctorId) ?? {
      id: item.doctorId,
      fullName: item.doctorName,
      specialtyName: item.specialtyName
    }
    const held = await flow.chooseEarliest({
      doctor,
      date: item.date,
      slot: { id: item.slotId, date: item.date, startTime: item.startTime, endTime: item.endTime }
    })
    if (!held) loadEarliest() // khung giờ vừa bị người khác giữ hoặc đặt: danh sách gợi ý đã cũ
  } finally {
    choosingEarliest.value = false
  }
}

watch(() => [flow.service.value?.id, earliestSpecialty.value], loadEarliest)

// Chuyển bước thì đưa focus về tiêu đề để người dùng bàn phím/trình đọc màn hình biết nội dung đã đổi
watch(
  () => flow.step.value,
  async (current) => {
    if (current === 1) loadEarliest() // quay lại bước dịch vụ: gợi ý cũ có thể đã có người đặt
    await nextTick()
    heading.value?.focus()
  }
)

onMounted(() => {
  loadDoctors()
  loadServices()
  loadSpecialties()
})
</script>
