<template>
  <DoctorLayout>
    <PageHeader title="Đánh giá về tôi" eyebrow="Bác sĩ" description="Đánh giá đang hiện của bệnh nhân sau các lần khám với bạn. Tên người đánh giá được rút gọn như trên trang công khai." />

    <section v-if="summary" class="mb-4 flex flex-wrap items-center gap-4 rounded-md border border-slate-200 bg-white p-5" aria-label="Tóm tắt đánh giá">
      <div>
        <p class="text-sm text-slate-600">Điểm trung bình</p>
        <StarRating :value="summary.averageRating" size="lg" show-value />
      </div>
      <p class="text-sm text-slate-700">{{ summary.reviewCount }} đánh giá</p>
      <ul class="flex flex-wrap gap-3 text-sm text-slate-700" aria-label="Phân bố số sao">
        <li v-for="stars in [5, 4, 3, 2, 1]" :key="stars">{{ stars }} sao: <strong>{{ summary.distribution?.[stars - 1] ?? 0 }}</strong></li>
      </ul>
    </section>

    <ReviewList :reviews="reviews" :loading="loading" :error="error" empty-text="Bạn chưa có đánh giá nào" />
    <Pagination class="mt-4" :page="page" :total-pages="totalPages" @update:page="changePage" />
  </DoctorLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { PageHeader, Pagination } from '@/components/ui'
import ReviewList from '@/components/reviews/ReviewList.vue'
import StarRating from '@/components/reviews/StarRating.vue'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { doctorsApi } from '@/api/doctors'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const summary = ref(null)
const reviews = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse } = usePagination({ size: 10 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorsApi.myReviews({ page: page.value, size: size.value })
    reviews.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    reviews.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function changePage(next) {
  page.value = next
  load()
}

onMounted(async () => {
  load()
  summary.value = await doctorsApi.myRatingSummary().catch(() => null)
})
</script>
