<template>
  <PatientLayout>
    <PageHeader title="Đánh giá của tôi" eyebrow="Bệnh nhân" description="Các đánh giá bạn đã gửi cho những lần khám đã hoàn thành. Mở chi tiết lịch hẹn để sửa hoặc xóa." />

    <DataTable :columns="columns" :rows="reviews" :loading="loading" :error="error" empty-text="Bạn chưa gửi đánh giá nào">
      <template #cell-doctorName="{ row }">
        <RouterLink class="font-semibold text-slate-950 hover:text-primary-700 hover:underline" :to="`/doctors/${row.doctorId}`">{{ row.doctorName }}</RouterLink>
        <div class="text-xs text-slate-600">{{ row.specialtyName || '' }}</div>
      </template>
      <template #cell-rating="{ row }">
        <StarRating :value="row.rating" />
      </template>
      <template #cell-comment="{ value }">
        <span class="line-clamp-2 whitespace-pre-line text-slate-700">{{ value || '—' }}</span>
      </template>
      <template #cell-status="{ row }">
        <StatusBadge :value="row.status" :label="reviewStatus[row.status] || row.status" />
        <p v-if="row.hiddenReason" class="mt-1 text-xs text-red-800">{{ row.hiddenReason }}</p>
      </template>
      <template #cell-createdAt="{ value }">{{ formatDate(value) }}</template>
      <template #cell-action="{ row }">
        <RouterLink class="text-sm font-semibold text-primary-700 hover:underline" :to="`/patient/appointments/${row.appointmentId}`" :aria-label="`Mở lịch hẹn #${row.appointmentId}`">
          Lịch hẹn #{{ row.appointmentId }}
        </RouterLink>
      </template>
      <template #footer>
        <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
      </template>
    </DataTable>
    <p class="mt-4 text-sm text-slate-600">
      Muốn đánh giá một lần khám khác? Mở <RouterLink class="font-semibold text-primary-700 underline" to="/patient/appointments">danh sách lịch hẹn</RouterLink> và chọn một lịch đã hoàn thành.
    </p>
  </PatientLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { DataTable, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import StarRating from '@/components/reviews/StarRating.vue'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { reviewsApi } from '@/api/reviews'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate } from '@/utils/formatters'
import { reviewStatus } from '@/utils/statusLabels'

const columns = [
  { key: 'doctorName', label: 'Bác sĩ' },
  { key: 'rating', label: 'Số sao' },
  { key: 'comment', label: 'Nhận xét' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'createdAt', label: 'Ngày gửi' },
  { key: 'action', label: 'Lần khám' }
]

const reviews = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse } = usePagination({ size: 10 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await reviewsApi.mine({ page: page.value, size: size.value })
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

onMounted(load)
</script>
