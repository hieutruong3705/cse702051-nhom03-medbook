<template>
  <PublicLayout>
    <PageHeader title="Đánh giá khám chữa bệnh" description="Đánh giá của người bệnh sau các lần khám đã hoàn thành tại MedBook." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <form class="mb-6 grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_1fr_200px_auto]" @submit.prevent="reload">
        <BaseSelect id="review-specialty" v-model="filters.specialtyId" label="Chuyên khoa" placeholder="Tất cả chuyên khoa" :options="specialtyOptions" />
        <BaseSelect id="review-doctor" v-model="filters.doctorId" label="Bác sĩ" placeholder="Tất cả bác sĩ" :options="doctorOptions" />
        <BaseSelect id="review-rating" v-model="filters.rating" label="Số sao" placeholder="Tất cả" :options="ratingOptions" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-filter">Lọc</BaseButton>
        </div>
      </form>
      <p class="mb-3 text-sm text-slate-600" aria-live="polite">{{ total }} đánh giá</p>
      <ReviewList :reviews="reviews" :loading="loading" :error="error" show-doctor empty-text="Chưa có đánh giá phù hợp" />
      <Pagination class="mt-4" :page="page" :total-pages="totalPages" @update:page="changePage" />
      <p class="mt-6 text-xs text-slate-600">
        Chỉ người bệnh đã hoàn thành lần khám mới gửi được đánh giá. Tên người đánh giá được rút gọn để bảo vệ thông tin cá nhân.
      </p>
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BaseButton, BaseSelect, PageHeader, Pagination } from '@/components/ui'
import ReviewList from '@/components/reviews/ReviewList.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { reviewsApi } from '@/api/reviews'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams } from '@/utils/params'

const route = useRoute()
const queryId = (value) => {
  const text = String((Array.isArray(value) ? value[0] : value) ?? '').trim()
  return /^\d+$/.test(text) ? text : ''
}

const filters = reactive({
  specialtyId: queryId(route.query.specialtyId),
  doctorId: queryId(route.query.doctorId),
  rating: ''
})
const ratingOptions = [5, 4, 3, 2, 1].map((stars) => ({ label: `${stars} sao`, value: String(stars) }))
const specialtyOptions = ref([])
const doctorOptions = ref([])
const reviews = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })

async function loadReviews() {
  loading.value = true
  error.value = ''
  try {
    const response = await reviewsApi.listPublic(cleanParams({ ...filters, page: page.value, size: size.value }))
    reviews.value = response?.content ?? []
    total.value = response?.totalElements ?? 0
    setPageResponse(response)
  } catch (err) {
    reviews.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return loadReviews()
}

function changePage(next) {
  page.value = next
  loadReviews()
}

onMounted(async () => {
  loadReviews()
  const [specialties, doctors] = await Promise.all([
    catalogApi.specialties.list({ size: 100 }).catch(() => null),
    doctorsApi.list({ size: 100 }).catch(() => null)
  ])
  specialtyOptions.value = (specialties?.content ?? []).map((item) => ({ label: item.name, value: String(item.id) }))
  doctorOptions.value = (doctors?.content ?? []).map((item) => ({ label: item.fullName, value: String(item.id) }))
})
</script>
