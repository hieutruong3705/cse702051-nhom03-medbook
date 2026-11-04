<script setup>
import { pickCarouselReviews } from '@/components/home/carouselReviews'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import heroImage from '@/assets/hero.webp'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import StarRating from '@/components/reviews/StarRating.vue'
import SpecialtyBadge from '@/components/ui/SpecialtyBadge.vue'
import DoctorAvatar from '@/components/booking/DoctorAvatar.vue'
import QuickBooking from '@/components/home/QuickBooking.vue'
import ClinicStats from '@/components/home/ClinicStats.vue'
import ReviewCarousel from '@/components/home/ReviewCarousel.vue'
import { clinicApi } from '@/api/clinic'
import { reviewsApi } from '@/api/reviews'
import { reveal as vReveal } from '@/utils/motion'
import { useAuthStore } from '@/stores/auth'

// Số thẻ chuyên khoa và bác sĩ hiện ở trang chủ; phần còn lại nằm ở trang danh sách ("Xem thêm").
const HOME_LIST_SIZE = 8

const router = useRouter()
const auth = useAuthStore()
const specialties = ref([])
const doctors = ref([])
const clinic = ref(null)
const reviews = ref([])
const loading = ref(true)
const keyword = ref('')

const services = [
  { id: 1, title: 'Khám chuyên khoa', icon: 'fa-solid fa-hospital', tone: 'text-primary-600' },
  { id: 2, title: 'Khám tổng quát', icon: 'fa-solid fa-clipboard-list', tone: 'text-emerald-600' },
  { id: 3, title: 'Xét nghiệm', icon: 'fa-solid fa-vial', tone: 'text-indigo-600' },
  { id: 4, title: 'Theo dõi hồ sơ', icon: 'fa-solid fa-file-medical', tone: 'text-amber-600' }
]

// Chỉ bệnh nhân đặt được lịch. Khách được chuyển qua trang đăng nhập rồi quay lại trang đặt lịch (route guard);
// bác sĩ và quản trị viên được đưa tới danh sách bác sĩ thay vì gặp trang "không có quyền".
const canBook = computed(() => !auth.isAuthenticated || auth.hasRole('PATIENT'))
const bookingPath = computed(() => (canBook.value ? '/patient/booking' : '/doctors'))

const listContent = (response) => (Array.isArray(response?.content) ? response.content : [])

onMounted(async () => {
  try {
    const [specRes, docRes, clinicRes, reviewRes] = await Promise.all([
      catalogApi.specialties.list({ size: HOME_LIST_SIZE }).catch(() => null),
      doctorsApi.list({ size: HOME_LIST_SIZE }).catch(() => null),
      clinicApi.get().catch(() => null),
      // Đánh giá công khai (VISIBLE) có điểm cao nhất cho băng chuyền
      reviewsApi.listPublic({ size: 20, sort: 'rating,desc' }).catch(() => null)
    ])
    specialties.value = listContent(specRes)
    doctors.value = listContent(docRes)
    clinic.value = clinicRes
    reviews.value = pickCarouselReviews(listContent(reviewRes))
  } finally {
    loading.value = false
  }
})

function search() {
  const text = keyword.value.trim()
  router.push({ path: '/doctors', query: text ? { keyword: text } : {} })
}

function openBooking() {
  router.push(bookingPath.value)
}
</script>

<template>
  <PublicLayout>
    <section class="relative overflow-hidden bg-slate-950 text-white">
      <img :src="heroImage" alt="" class="absolute inset-0 h-full w-full object-cover" />
      <!-- Lớp phủ tối ở phía có chữ để chữ trắng đủ tương phản; sáng dần sang phải để thấy ảnh. Màn hình hẹp phủ đều hơn. -->
      <div
        class="absolute inset-0 bg-linear-to-r from-slate-950/90 via-slate-950/75 to-slate-950/60 lg:via-slate-950/65 lg:to-slate-950/15"
        aria-hidden="true"
      ></div>
      <div class="relative mx-auto grid min-h-[520px] max-w-7xl grid-cols-1 items-center gap-10 px-4 py-16 sm:px-6 lg:grid-cols-[minmax(0,1fr)_420px] lg:px-8">
        <div class="max-w-3xl">
          <h1 class="text-4xl font-bold sm:text-5xl">MedBook</h1>
          <p class="mt-5 max-w-2xl text-lg text-slate-100">
            Đặt lịch khám trực tuyến với bác sĩ bạn chọn, theo dõi lịch hẹn, kết quả khám và đơn thuốc ở cùng một nơi.
          </p>
          <form class="mt-8 flex max-w-xl items-center rounded-md bg-white p-2 shadow-lg" role="search" @submit.prevent="search">
            <i class="fa-solid fa-magnifying-glass px-3 text-slate-500" aria-hidden="true"></i>
            <label for="home-search" class="sr-only">Tìm bác sĩ hoặc chuyên khoa</label>
            <input
              id="home-search"
              v-model="keyword"
              class="min-w-0 flex-1 px-2 py-3 text-slate-900 outline-none"
              type="search"
              maxlength="100"
              placeholder="Tìm bác sĩ hoặc chuyên khoa"
            />
            <button class="rounded-md bg-primary-600 px-4 py-3 text-sm font-semibold text-white hover:bg-primary-700" type="submit">
              Tìm
            </button>
          </form>
          <button
            class="mt-4 rounded-md border border-white/70 px-4 py-3 text-sm font-semibold text-white hover:bg-white/10"
            type="button"
            @click="openBooking"
          >
            <i class="fa-solid fa-calendar-plus mr-2" aria-hidden="true"></i>Đặt khám
          </button>
        </div>
        <QuickBooking :specialties="specialties" />
      </div>
    </section>

    <section v-if="clinic" class="bg-slate-50 py-8">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="sr-only">MedBook qua các con số</h2>
        <ClinicStats :clinic="clinic" />
      </div>
    </section>

    <section class="bg-white py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-slate-950">Dịch vụ toàn diện</h2>
        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button
            v-for="(service, index) in services"
            v-reveal="index"
            :key="service.id"
            type="button"
            class="rounded-md border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
            @click="openBooking"
          >
            <i :class="[service.icon, service.tone, 'text-3xl']" aria-hidden="true"></i>
            <h3 class="mt-4 text-lg font-semibold text-slate-950">{{ service.title }}</h3>
            <p class="mt-2 text-sm text-slate-600">Chọn bác sĩ, xem giờ trống và hoàn tất đặt lịch sau khi đăng nhập.</p>
          </button>
        </div>
      </div>
    </section>

    <section class="bg-slate-100 py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between gap-4">
          <h2 class="text-2xl font-bold text-slate-950">Chuyên khoa phổ biến</h2>
          <RouterLink to="/specialties" class="text-sm font-semibold text-primary-700 hover:text-primary-900">Xem thêm</RouterLink>
        </div>
        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <RouterLink
            v-for="(spec, index) in specialties"
            v-reveal="index"
            :key="spec.id"
            :to="`/specialties/${spec.id}`"
            class="group rounded-md border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
            data-test="home-specialty"
          >
            <SpecialtyBadge :specialty="spec" variant="icon" size="lg" />
            <h3 class="mt-4 font-semibold text-slate-950 group-hover:text-primary-700">{{ spec.name }}</h3>
          </RouterLink>
          <p v-if="specialties.length === 0 && !loading" class="col-span-full text-slate-600">Danh sách chuyên khoa đang được cập nhật.</p>
        </div>
      </div>
    </section>

    <section class="bg-white py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between gap-4">
          <h2 class="text-2xl font-bold text-slate-950">Bác sĩ</h2>
          <RouterLink to="/doctors" class="text-sm font-semibold text-primary-700 hover:text-primary-900">Xem thêm</RouterLink>
        </div>
        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <RouterLink
            v-for="(doctor, index) in doctors"
            v-reveal="index"
            :key="doctor.id"
            :to="`/doctors/${doctor.id}`"
            class="rounded-md border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:shadow-md"
          >
            <DoctorAvatar :doctor-id="doctor.id" />
            <h3 class="mt-4 font-semibold text-slate-950">{{ doctor.fullName }}</h3>
            <SpecialtyBadge class="mt-1" :specialty="doctor.specialtyName ? doctor : null" />
            <div class="mt-2 flex items-center gap-2 text-sm text-slate-700">
              <StarRating :value="doctor.averageRating" show-value />
              <span>{{ doctor.reviewCount || 0 }} đánh giá</span>
            </div>
          </RouterLink>
          <p v-if="doctors.length === 0 && !loading" class="col-span-full text-slate-600">Danh sách bác sĩ đang được cập nhật.</p>
        </div>
      </div>
    </section>
    <section v-if="reviews.length" class="bg-slate-100 py-12">
      <div v-reveal class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <ReviewCarousel :reviews="reviews" />
      </div>
    </section>
  </PublicLayout>
</template>
