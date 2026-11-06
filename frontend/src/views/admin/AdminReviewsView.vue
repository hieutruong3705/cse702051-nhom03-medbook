<template>
  <AdminLayout>
    <PageHeader title="Kiểm duyệt đánh giá" eyebrow="Quản trị" description="Đánh giá hiện ngay khi bệnh nhân gửi. Ẩn đánh giá vi phạm kèm lý do; mọi thao tác ẩn, hiện đều được ghi nhật ký." />

    <form class="mb-4 grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[200px_1fr_160px_auto]" @submit.prevent="reload">
      <BaseSelect id="admin-review-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
      <BaseSelect id="admin-review-doctor" v-model="filters.doctorId" label="Bác sĩ" placeholder="Tất cả bác sĩ" :options="doctorOptions" />
      <BaseSelect id="admin-review-rating" v-model="filters.rating" label="Số sao" placeholder="Tất cả" :options="ratingOptions" />
      <div class="flex items-end">
        <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-filter">Lọc</BaseButton>
      </div>
    </form>

    <DataTable :columns="columns" :rows="reviews" :loading="loading" :error="error" empty-text="Không có đánh giá phù hợp">
      <template #cell-doctorName="{ row }">
        <span class="font-semibold text-slate-950">{{ row.doctorName }}</span>
        <div class="text-xs text-slate-600">Lịch hẹn #{{ row.appointmentId }}</div>
      </template>
      <template #cell-patientName="{ row }">
        {{ row.patientName }}
        <div class="text-xs text-slate-600">{{ row.patientCode }}</div>
      </template>
      <template #cell-rating="{ row }"><StarRating :value="row.rating" /></template>
      <template #cell-comment="{ value }">
        <span class="line-clamp-3 whitespace-pre-line text-slate-700">{{ value || '—' }}</span>
      </template>
      <template #cell-status="{ row }">
        <StatusBadge :value="row.status" :label="reviewStatus[row.status] || row.status" />
        <p v-if="row.hiddenReason" class="mt-1 text-xs text-slate-700">{{ row.hiddenReason }}</p>
      </template>
      <template #cell-createdAt="{ value }">{{ formatDateTime(value) }}</template>
      <template #cell-actions="{ row }">
        <BaseButton v-if="row.status === 'VISIBLE'" variant="danger" icon="fa-regular fa-eye-slash" @click="openHide(row)">Ẩn</BaseButton>
        <BaseButton v-else variant="secondary" icon="fa-regular fa-eye" :loading="actingId === row.id" @click="show(row)">Hiện lại</BaseButton>
      </template>
      <template #footer>
        <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
      </template>
    </DataTable>

    <FormModal v-model="hideOpen" title="Ẩn đánh giá" description="Đánh giá sẽ biến khỏi trang công khai và không còn tính vào điểm trung bình. Bệnh nhân thấy lý do bạn ghi.">
      <BaseTextarea id="hide-reason" v-model="hideReason" label="Lý do ẩn" required :rows="3" :error="hideError" />
      <template #footer>
        <BaseButton variant="secondary" :disabled="actingId !== null" @click="hideOpen = false">Hủy</BaseButton>
        <BaseButton variant="danger" :loading="actingId !== null" @click="confirmHide">Ẩn đánh giá</BaseButton>
      </template>
    </FormModal>
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseSelect, BaseTextarea, DataTable, FormModal, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import StarRating from '@/components/reviews/StarRating.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { doctorsApi } from '@/api/doctors'
import { reviewsApi } from '@/api/reviews'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'
import { cleanParams, fieldErrors } from '@/utils/params'
import { reviewStatus } from '@/utils/statusLabels'

const columns = [
  { key: 'doctorName', label: 'Bác sĩ' },
  { key: 'patientName', label: 'Bệnh nhân' },
  { key: 'rating', label: 'Số sao' },
  { key: 'comment', label: 'Nhận xét' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'createdAt', label: 'Ngày gửi' },
  { key: 'actions', label: 'Thao tác' }
]
const statusOptions = Object.entries(reviewStatus).map(([value, label]) => ({ value, label }))
const ratingOptions = [5, 4, 3, 2, 1].map((stars) => ({ label: `${stars} sao`, value: String(stars) }))

const toast = useToast()
const filters = reactive({ status: '', doctorId: '', rating: '' })
const doctorOptions = ref([])
const reviews = ref([])
const loading = ref(false)
const error = ref('')
const hideOpen = ref(false)
const hideTarget = ref(null)
const hideReason = ref('')
const hideError = ref('')
const actingId = ref(null)
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await reviewsApi.listAdmin(cleanParams({ ...filters, page: page.value, size: size.value }))
    reviews.value = response?.content ?? []
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
  return load()
}

function changePage(next) {
  page.value = next
  load()
}

function openHide(row) {
  hideTarget.value = row
  hideReason.value = ''
  hideError.value = ''
  hideOpen.value = true
}

async function confirmHide() {
  if (!hideReason.value.trim()) {
    hideError.value = 'Phải nhập lý do ẩn đánh giá'
    return
  }
  actingId.value = hideTarget.value.id
  try {
    await reviewsApi.hide(hideTarget.value.id, hideReason.value.trim())
    hideOpen.value = false
    toast.success('Đã ẩn đánh giá')
    await load()
  } catch (err) {
    hideError.value = fieldErrors(err).reason || apiErrorMessage(err)
  } finally {
    actingId.value = null
  }
}

async function show(row) {
  actingId.value = row.id
  try {
    await reviewsApi.show(row.id)
    toast.success('Đã hiện lại đánh giá')
    await load()
  } catch (err) {
    toast.error(apiErrorMessage(err))
  } finally {
    actingId.value = null
  }
}

onMounted(async () => {
  load()
  const doctors = await doctorsApi.listAdmin({ size: 100 }).catch(() => null)
  doctorOptions.value = (doctors?.content ?? []).map((item) => ({ label: item.fullName, value: String(item.id) }))
})
</script>
