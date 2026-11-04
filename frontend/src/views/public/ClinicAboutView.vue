<template>
  <PublicLayout>
    <PageHeader eyebrow="Giới thiệu" :title="clinic?.name || 'Phòng khám MedBook'" description="Thông tin phòng khám, giờ làm việc và cảm nhận của người bệnh." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <Skeleton v-else-if="loading && !clinic" class="h-40" />

      <template v-else-if="clinic">
        <p class="max-w-3xl text-base leading-7 text-slate-700">{{ clinic.introduction }}</p>

        <ul class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Số liệu phòng khám">
          <li v-for="stat in stats" :key="stat.label" class="rounded-md border border-slate-200 bg-white p-5">
            <p class="text-sm text-slate-600">{{ stat.label }}</p>
            <p class="mt-1 text-2xl font-bold text-slate-950">{{ stat.value }}</p>
            <RouterLink v-if="stat.to" class="mt-2 inline-block text-sm font-medium text-primary-700 hover:underline" :to="stat.to">{{ stat.link }}</RouterLink>
          </li>
        </ul>

        <div class="mt-8 grid gap-6 lg:grid-cols-2">
          <section class="rounded-md border border-slate-200 bg-white p-6" aria-labelledby="clinic-contact">
            <h2 id="clinic-contact" class="text-lg font-semibold text-slate-950">Liên hệ</h2>
            <dl class="mt-3 space-y-3 text-sm">
              <div class="flex gap-3">
                <dt class="w-24 shrink-0 font-semibold text-slate-900">Địa chỉ</dt>
                <dd class="text-slate-700">{{ clinic.address }}</dd>
              </div>
              <div class="flex gap-3">
                <dt class="w-24 shrink-0 font-semibold text-slate-900">Điện thoại</dt>
                <dd class="text-slate-700">{{ clinic.phone }}</dd>
              </div>
              <div class="flex gap-3">
                <dt class="w-24 shrink-0 font-semibold text-slate-900">Email</dt>
                <dd class="text-slate-700">{{ clinic.email }}</dd>
              </div>
            </dl>
            <p class="mt-4 rounded bg-amber-50 px-3 py-2 text-xs text-amber-900">Dữ liệu mô phỏng phục vụ mục đích học tập, không phải phòng khám thật.</p>
          </section>

          <section class="rounded-md border border-slate-200 bg-white p-6" aria-labelledby="clinic-hours">
            <h2 id="clinic-hours" class="text-lg font-semibold text-slate-950">Giờ làm việc</h2>
            <ul class="mt-3 space-y-2 text-sm text-slate-700">
              <li v-for="line in clinic.workingHours" :key="line" class="flex items-center gap-2">
                <i class="fa-regular fa-clock text-primary-700" aria-hidden="true"></i>{{ line }}
              </li>
            </ul>
            <RouterLink
              class="mt-4 inline-flex items-center gap-2 rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2"
              to="/doctors"
            >
              <i class="fa-solid fa-user-doctor" aria-hidden="true"></i>Chọn bác sĩ và đặt lịch
            </RouterLink>
          </section>
        </div>

        <section class="mt-8" aria-labelledby="clinic-reviews">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 id="clinic-reviews" class="text-lg font-semibold text-slate-950">Người bệnh nói gì về MedBook</h2>
            <RouterLink class="text-sm font-medium text-primary-700 hover:underline" to="/reviews">Xem tất cả đánh giá</RouterLink>
          </div>
          <ReviewList class="mt-3" :reviews="reviews" show-doctor empty-text="Chưa có đánh giá nào" />
        </section>
      </template>
    </section>
  </PublicLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { PageHeader, Skeleton } from '@/components/ui'
import ReviewList from '@/components/reviews/ReviewList.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { clinicApi } from '@/api/clinic'
import { reviewsApi } from '@/api/reviews'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const clinic = ref(null)
const reviews = ref([])
const loading = ref(false)
const error = ref('')

const stats = computed(() => [
  { label: 'Bác sĩ đang nhận khám', value: clinic.value.doctorCount, to: '/doctors', link: 'Xem bác sĩ' },
  { label: 'Chuyên khoa', value: clinic.value.specialtyCount, to: '/specialties', link: 'Xem chuyên khoa' },
  { label: 'Dịch vụ khám', value: clinic.value.serviceCount, to: '/services', link: 'Xem bảng giá' },
  {
    label: 'Điểm đánh giá trung bình',
    value: clinic.value.averageRating ? `${clinic.value.averageRating.toFixed(1)}/5` : 'Chưa có',
    to: '/reviews',
    link: `${clinic.value.reviewCount} đánh giá`
  }
])

onMounted(async () => {
  loading.value = true
  try {
    const [info, latest] = await Promise.all([clinicApi.get(), reviewsApi.listPublic({ size: 3, sort: 'rating,desc' })])
    clinic.value = info
    reviews.value = latest?.content ?? []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>
