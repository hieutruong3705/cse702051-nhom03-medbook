<template>
  <PublicLayout>
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <nav class="mb-4 text-sm text-slate-600" aria-label="Đường dẫn">
        <RouterLink class="text-primary-700 underline" to="/specialties">Chuyên khoa</RouterLink>
        <span aria-hidden="true"> / </span>
        <span>{{ specialty?.name || 'Chi tiết chuyên khoa' }}</span>
      </nav>

      <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <Skeleton v-else-if="loading && !specialty" class="h-40" />

      <template v-else-if="specialty">
        <header class="rounded-md border border-slate-200 bg-white p-6">
          <div class="flex items-center gap-4">
            <SpecialtyBadge :specialty="specialty" variant="icon" />
            <div>
              <h1 class="text-2xl font-bold text-slate-950">{{ specialty.name }}</h1>
              <p class="mt-1 text-sm text-slate-600">{{ doctorsTotal }} bác sĩ đang nhận khám</p>
            </div>
          </div>
          <p class="mt-4 whitespace-pre-line text-sm leading-6 text-slate-700">{{ specialty.description || 'Chuyên khoa trong hệ thống MedBook.' }}</p>
        </header>

        <section class="mt-6" aria-labelledby="specialty-doctors">
          <h2 id="specialty-doctors" class="text-lg font-semibold text-slate-950">Bác sĩ của chuyên khoa</h2>
          <ul v-if="doctors.length" class="mt-3 space-y-4" aria-label="Bác sĩ của chuyên khoa">
            <li v-for="item in doctors" :key="item.id"><DoctorCard :doctor="item" heading-tag="h3" /></li>
          </ul>
          <EmptyState
            v-else-if="!loading"
            class="mt-3"
            illustration="search"
            heading-tag="h3"
            title="Chuyên khoa chưa có bác sĩ nhận khám"
            message="Bạn có thể xem bác sĩ của các chuyên khoa khác."
          >
            <RouterLink class="text-sm font-semibold text-primary-700 underline" to="/doctors">Xem tất cả bác sĩ</RouterLink>
          </EmptyState>
        </section>

        <section class="mt-8" aria-labelledby="specialty-reviews">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 id="specialty-reviews" class="text-lg font-semibold text-slate-950">Đánh giá gần đây</h2>
            <RouterLink class="text-sm font-medium text-primary-700 underline" :to="`/reviews?specialtyId=${specialty.id}`">Xem tất cả đánh giá</RouterLink>
          </div>
          <ReviewList class="mt-3" :reviews="reviews" show-doctor empty-text="Chuyên khoa chưa có đánh giá" />
        </section>
      </template>
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { EmptyState, Skeleton, SpecialtyBadge } from '@/components/ui'
import ReviewList from '@/components/reviews/ReviewList.vue'
import DoctorCard from '@/components/booking/DoctorCard.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { doctorsApi } from '@/api/doctors'
import { reviewsApi } from '@/api/reviews'
import { catalogApi } from '@/api/catalog'
import { updatePageMeta } from '@/router/guards'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const route = useRoute()
const specialty = ref(null)
const doctors = ref([])
const doctorsTotal = ref(0)
const reviews = ref([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    const [detail, doctorPage, reviewPage] = await Promise.all([
      catalogApi.specialties.get(id),
      doctorsApi.list({ specialtyId: id, size: 30 }),
      reviewsApi.listPublic({ specialtyId: id, size: 3 })
    ])
    specialty.value = detail
    doctors.value = doctorPage?.content ?? []
    doctorsTotal.value = doctorPage?.totalElements ?? doctors.value.length
    reviews.value = reviewPage?.content ?? []
    updatePageMeta({
      title: `Chuyên khoa ${detail.name}`,
      description: `Chuyên khoa ${detail.name} tại phòng khám MedBook: bác sĩ đang nhận khám và đánh giá của bệnh nhân.`
    })
  } catch (err) {
    specialty.value = null
    error.value = err?.status === 404 ? 'Không tìm thấy chuyên khoa hoặc chuyên khoa đã ngừng nhận khám.' : apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, (next, prev) => {
  if (next && next !== prev) load()
})
onMounted(load)
</script>
