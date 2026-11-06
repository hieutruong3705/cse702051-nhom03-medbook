<template>
  <section class="rounded-lg bg-white p-5 text-slate-900 shadow-xl" aria-labelledby="quick-booking-title" data-test="quick-booking">
    <h2 id="quick-booking-title" class="flex items-center gap-2 text-lg font-bold text-slate-950">
      <i class="fa-solid fa-bolt text-amber-600" aria-hidden="true"></i>Đặt lịch nhanh
    </h2>
    <p class="mt-1 text-sm text-slate-600">Chọn chuyên khoa và ngày, MedBook tìm các giờ trống sớm nhất.</p>

    <form class="mt-4 grid gap-3 sm:grid-cols-2" novalidate @submit.prevent="find">
      <div>
        <label for="quick-specialty" class="mb-1 block text-sm font-medium text-slate-700">Chuyên khoa</label>
        <select id="quick-specialty" v-model="specialtyId" class="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-600/20">
          <option value="">Tất cả chuyên khoa</option>
          <option v-for="item in specialties" :key="item.id" :value="String(item.id)">{{ item.name }}</option>
        </select>
      </div>
      <div>
        <label for="quick-date" class="mb-1 block text-sm font-medium text-slate-700">Ngày khám</label>
        <select id="quick-date" v-model="date" class="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-600/20">
          <option v-for="day in days" :key="day.value" :value="day.value">{{ day.label }}</option>
        </select>
      </div>
      <button
        type="submit"
        class="inline-flex items-center justify-center gap-2 rounded-md bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2 disabled:opacity-60 sm:col-span-2"
        :disabled="searching"
        data-test="quick-submit"
      >
        <i :class="searching ? 'fa-solid fa-spinner fa-spin' : 'fa-solid fa-magnifying-glass'" aria-hidden="true"></i>
        {{ searching ? 'Đang tìm…' : 'Tìm giờ trống' }}
      </button>
    </form>

    <!-- Kết quả đọc cho trình đọc màn hình khi tìm xong -->
    <div class="mt-4" aria-live="polite" data-test="quick-results">
      <template v-if="searched && !searching">
        <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error }}</p>
        <template v-else-if="results.length">
          <p class="text-sm font-semibold text-slate-800">{{ results.length }} giờ trống sớm nhất {{ dayLabel }}</p>
          <ul class="mt-2 space-y-2">
            <li v-for="item in results" :key="item.slot.id">
              <component
                :is="canBook ? 'RouterLink' : 'div'"
                v-bind="canBook ? { to: bookingLink(item.doctor.id, item.slot, date) } : {}"
                class="flex items-center gap-3 rounded-md border border-slate-200 px-3 py-2 text-sm"
                :class="canBook ? 'transition hover:border-primary-600 hover:bg-primary-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600' : ''"
                data-test="quick-slot"
              >
                <span class="rounded bg-primary-700 px-2 py-1 font-bold text-white">{{ formatTime(item.slot.startTime) }}</span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-semibold text-slate-900">{{ item.doctor.fullName }}</span>
                  <span class="block truncate text-xs text-slate-600">{{ item.doctor.specialtyName || 'Chưa có chuyên khoa' }}</span>
                </span>
                <i v-if="canBook" class="fa-solid fa-chevron-right text-slate-500" aria-hidden="true"></i>
              </component>
            </li>
          </ul>
          <p v-if="!canBook" class="mt-2 text-xs text-slate-600">Chỉ tài khoản bệnh nhân đặt được lịch trực tuyến.</p>
        </template>
        <p v-else class="text-sm text-slate-700">
          Không còn giờ trống {{ dayLabel }}. Hãy chọn ngày khác hoặc
          <RouterLink class="font-semibold text-primary-700 underline" to="/doctors">xem tất cả bác sĩ</RouterLink>.
        </p>
      </template>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { doctorsApi } from '@/api/doctors'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatTime } from '@/utils/formatters'
import { bookingLink, earliestSlots, upcomingDays } from '@/components/booking/bookingLink'

defineProps({
  specialties: { type: Array, default: () => [] }
})

// Số bác sĩ tối đa được hỏi lịch trong một lần tìm (giới hạn số lời gọi API)
const DOCTOR_LIMIT = 10

const auth = useAuthStore()
const canBook = computed(() => !auth.isAuthenticated || auth.hasRole('PATIENT'))
const days = upcomingDays(7)
const specialtyId = ref('')
const date = ref(days[0].value)
const searching = ref(false)
const searched = ref(false)
const error = ref('')
const results = ref([])
const dayLabel = computed(() => {
  const day = days.find((item) => item.value === date.value)
  return day ? (day.weekday === 'Hôm nay' ? 'hôm nay' : `${day.weekday} ${day.dm}`) : ''
})

async function find() {
  searching.value = true
  searched.value = true
  error.value = ''
  try {
    const params = { size: DOCTOR_LIMIT }
    if (specialtyId.value) params.specialtyId = Number(specialtyId.value)
    const page = await doctorsApi.list(params)
    const doctors = Array.isArray(page?.content) ? page.content : []
    const perDoctor = await Promise.all(
      doctors.map(async (doctor) => ({ doctor, slots: await doctorsApi.slots(doctor.id, date.value).catch(() => []) }))
    )
    results.value = earliestSlots(perDoctor)
  } catch (err) {
    results.value = []
    error.value = apiErrorMessage(err)
  } finally {
    searching.value = false
  }
}
</script>
