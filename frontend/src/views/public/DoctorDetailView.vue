<template>
  <PublicLayout>
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <nav class="mb-4 text-sm text-slate-600" aria-label="Đường dẫn">
        <RouterLink class="text-primary-700 underline" to="/doctors">Bác sĩ</RouterLink>
        <span aria-hidden="true"> / </span>
        <span>{{ doctor?.fullName || 'Chi tiết bác sĩ' }}</span>
      </nav>

      <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <Skeleton v-else-if="loading && !doctor" class="h-48" />

      <template v-else-if="doctor">
        <article class="grid gap-6 lg:grid-cols-[2fr_1fr]">
          <div class="space-y-6">
            <header class="flex flex-col gap-4 rounded-md border border-slate-200 bg-white p-6 sm:flex-row sm:items-center">
              <span class="inline-flex h-20 w-20 shrink-0 items-center justify-center rounded-full bg-primary-50 text-2xl font-bold text-primary-700" aria-hidden="true">
                {{ initials }}
              </span>
              <div class="min-w-0">
                <p v-if="doctor.academicTitle" class="text-sm font-semibold text-primary-700">{{ doctor.academicTitle }}</p>
                <h1 class="text-2xl font-bold text-slate-950">{{ doctor.fullName }}</h1>
                <p class="mt-1 text-sm text-slate-600">
                  <RouterLink v-if="doctor.specialtyId" class="font-medium text-primary-700 underline" :to="`/specialties/${doctor.specialtyId}`">
                    {{ doctor.specialtyName }}
                  </RouterLink>
                  <span v-else>Chưa có chuyên khoa</span>
                  <span v-if="doctor.yearsOfExperience != null"> · {{ doctor.yearsOfExperience }} năm kinh nghiệm</span>
                </p>
                <div class="mt-2 flex items-center gap-2 text-sm text-slate-700">
                  <StarRating :value="doctor.averageRating" show-value />
                  <span>{{ doctor.reviewCount }} đánh giá</span>
                </div>
              </div>
            </header>

            <section class="rounded-md border border-slate-200 bg-white p-6" aria-labelledby="doctor-intro">
              <h2 id="doctor-intro" class="text-lg font-semibold text-slate-950">Giới thiệu</h2>
              <p class="mt-2 whitespace-pre-line text-sm leading-6 text-slate-700">{{ doctor.bio || 'Bác sĩ chưa cập nhật phần giới thiệu.' }}</p>
              <dl class="mt-4 grid gap-4 sm:grid-cols-2">
                <div v-for="item in details" :key="item.label">
                  <dt class="text-sm font-semibold text-slate-900">{{ item.label }}</dt>
                  <dd class="mt-1 whitespace-pre-line text-sm text-slate-700">{{ item.value || 'Chưa cập nhật' }}</dd>
                </div>
              </dl>
            </section>

            <section class="rounded-md border border-slate-200 bg-white p-6" aria-labelledby="doctor-reviews">
              <div class="flex flex-wrap items-end justify-between gap-3">
                <h2 id="doctor-reviews" class="text-lg font-semibold text-slate-950">Đánh giá của bệnh nhân</h2>
                <BaseSelect id="doctor-review-rating" v-model="ratingFilter" label="Lọc theo số sao" placeholder="Tất cả" :options="ratingOptions" class="w-48" />
              </div>
              <div v-if="summary && summary.reviewCount" class="mt-4 grid gap-1" aria-label="Phân bố số sao">
                <div v-for="stars in [5, 4, 3, 2, 1]" :key="stars" class="flex items-center gap-2 text-sm text-slate-700">
                  <span class="w-12">{{ stars }} sao</span>
                  <span class="h-2 flex-1 rounded bg-slate-100" aria-hidden="true">
                    <span class="block h-2 rounded bg-amber-500" :style="{ width: barWidth(stars) }"></span>
                  </span>
                  <span class="w-8 text-right">{{ summary.distribution[stars - 1] }}</span>
                </div>
              </div>
              <ReviewList class="mt-4" :reviews="reviews" :loading="reviewsLoading" :error="reviewsError" empty-text="Bác sĩ chưa có đánh giá phù hợp" />
              <Pagination class="mt-3" :page="page" :total-pages="totalPages" @update:page="changePage" />
            </section>
          </div>

          <aside class="space-y-4">
            <section class="rounded-md border border-slate-200 bg-white p-6" aria-labelledby="doctor-slots">
              <h2 id="doctor-slots" class="text-lg font-semibold text-slate-950">Giờ khám còn trống</h2>
              <p class="mt-1 text-sm text-slate-600">Trong 7 ngày tới, theo lịch làm việc của bác sĩ.</p>
              <ul v-if="slotDays.length" class="mt-3 space-y-2">
                <li v-for="day in slotDays" :key="day.date" class="text-sm">
                  <span class="font-medium text-slate-900">{{ formatDate(day.date) }}</span>
                  <span class="text-slate-600"> · {{ day.count }} khung giờ, sớm nhất {{ day.first }}</span>
                </li>
              </ul>
              <p v-else-if="!slotsLoading" class="mt-3 text-sm text-slate-600">Chưa có giờ trống trong 7 ngày tới.</p>
              <RouterLink
                v-if="canBook"
                class="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2"
                :to="`/patient/booking?doctorId=${doctor.id}`"
              >
                <i class="fa-solid fa-calendar-plus" aria-hidden="true"></i>Đặt lịch với bác sĩ
              </RouterLink>
              <p v-if="!auth.isAuthenticated" class="mt-2 text-xs text-slate-600">Bạn sẽ được chuyển tới trang đăng nhập trước khi đặt lịch.</p>
            </section>
          </aside>
        </article>
      </template>
    </section>
  </PublicLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { BaseSelect, Pagination, Skeleton } from '@/components/ui'
import ReviewList from '@/components/reviews/ReviewList.vue'
import StarRating from '@/components/reviews/StarRating.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { doctorsApi } from '@/api/doctors'
import { reviewsApi } from '@/api/reviews'
import { usePagination } from '@/composables/usePagination'
import { updatePageMeta } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatTime } from '@/utils/formatters'
import { cleanParams } from '@/utils/params'

const SLOT_DAYS = 7

const route = useRoute()
const auth = useAuthStore()
const doctor = ref(null)
const summary = ref(null)
const loading = ref(false)
const error = ref('')
const reviews = ref([])
const reviewsLoading = ref(false)
const reviewsError = ref('')
const ratingFilter = ref('')
const slotDays = ref([])
const slotsLoading = ref(false)
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 5 })

const ratingOptions = [5, 4, 3, 2, 1].map((stars) => ({ label: `${stars} sao`, value: String(stars) }))
const canBook = computed(() => !auth.isAuthenticated || auth.hasRole('PATIENT'))
const initials = computed(() =>
  (doctor.value?.fullName || '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0].toUpperCase())
    .join('')
)
const details = computed(() => [
  { label: 'Thế mạnh chuyên môn', value: doctor.value?.expertise },
  { label: 'Quá trình đào tạo', value: doctor.value?.education },
  { label: 'Quá trình công tác', value: doctor.value?.workHistory },
  { label: 'Học hàm, học vị', value: doctor.value?.academicTitle }
])

function barWidth(stars) {
  const total = summary.value?.reviewCount || 0
  return total ? `${Math.round((summary.value.distribution[stars - 1] / total) * 100)}%` : '0%'
}

async function loadReviews() {
  reviewsLoading.value = true
  reviewsError.value = ''
  try {
    const response = await reviewsApi.forDoctor(route.params.id, cleanParams({ rating: ratingFilter.value, page: page.value, size: size.value }))
    reviews.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    reviews.value = []
    reviewsError.value = apiErrorMessage(err)
  } finally {
    reviewsLoading.value = false
  }
}

function changePage(next) {
  page.value = next
  loadReviews()
}

// Gom giờ trống 7 ngày tới theo ngày; mỗi ngày một yêu cầu, chạy song song.
async function loadSlots(doctorId) {
  slotsLoading.value = true
  const today = new Date()
  const dates = Array.from({ length: SLOT_DAYS }, (_, i) => {
    const day = new Date(today.getFullYear(), today.getMonth(), today.getDate() + i)
    return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
  })
  const results = await Promise.all(dates.map((date) => doctorsApi.slots(doctorId, date).catch(() => [])))
  slotDays.value = dates
    .map((date, i) => ({ date, slots: Array.isArray(results[i]) ? results[i] : [] }))
    .filter((day) => day.slots.length)
    .map((day) => ({ date: day.date, count: day.slots.length, first: formatTime(day.slots[0].startTime) }))
  slotsLoading.value = false
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    const [profile, rating] = await Promise.all([doctorsApi.get(id), reviewsApi.summary(id)])
    doctor.value = profile
    summary.value = rating
    updatePageMeta({
      title: profile.fullName,
      description: `${profile.fullName}${profile.specialtyName ? `, ${profile.specialtyName}` : ''} tại phòng khám MedBook: giới thiệu, kinh nghiệm, đánh giá của bệnh nhân và giờ khám còn trống.`
    })
    loadReviews()
    loadSlots(id)
  } catch (err) {
    doctor.value = null
    error.value = err?.status === 404 ? 'Không tìm thấy bác sĩ hoặc bác sĩ đã ngừng nhận lịch.' : apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

watch(ratingFilter, () => {
  reset()
  loadReviews()
})
watch(() => route.params.id, (next, prev) => {
  if (next && next !== prev) load()
})
onMounted(load)
</script>
