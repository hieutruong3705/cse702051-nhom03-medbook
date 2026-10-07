<template>
  <PublicLayout>
    <PageHeader title="Bác sĩ" description="Tìm bác sĩ theo tên hoặc chuyên khoa rồi đặt lịch khám." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <form class="mb-6 grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_240px_auto]" @submit.prevent="reload">
        <BaseInput id="doctor-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Tên bác sĩ hoặc chuyên khoa" />
        <BaseSelect id="doctor-specialty" v-model="filters.specialtyId" label="Chuyên khoa" placeholder="Tất cả chuyên khoa" :options="specialtyOptions" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass">Tìm</BaseButton>
        </div>
      </form>

      <DataTable :columns="columns" :rows="doctors" :loading="loading" :error="error" empty-text="Chưa có bác sĩ phù hợp">
        <template #cell-fullName="{ row }">
          <div class="font-semibold text-slate-950">{{ row.fullName }}</div>
          <div class="text-xs text-slate-500">{{ row.specialtyName || 'Chưa có chuyên khoa' }}</div>
        </template>
        <template #cell-bio="{ value }">
          <span class="line-clamp-2 text-slate-600">{{ value || '' }}</span>
        </template>
        <template #cell-action="{ row }">
          <RouterLink
            class="inline-flex items-center gap-1 whitespace-nowrap rounded-md bg-primary-600 px-3 py-2 text-sm font-semibold text-white hover:bg-primary-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2"
            :to="`/patient/booking?doctorId=${row.id}`"
            :aria-label="`Đặt lịch với ${row.fullName}`"
          >
            <i class="fa-solid fa-calendar-plus" aria-hidden="true"></i>Đặt lịch
          </RouterLink>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BaseButton, BaseInput, BaseSelect, DataTable, PageHeader, Pagination } from '@/components/ui'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams } from '@/utils/params'

// Hồ sơ công khai của bác sĩ chỉ có họ tên, chuyên khoa và giới thiệu (không có số giấy phép hay điện thoại).
const columns = [
  { key: 'fullName', label: 'Bác sĩ' },
  { key: 'bio', label: 'Giới thiệu' },
  { key: 'action', label: 'Đặt lịch' }
]

// Trang chủ dẫn sang đây kèm `?keyword=` (ô tìm kiếm) hoặc `?specialtyId=` (thẻ chuyên khoa).
const route = useRoute()
const queryText = (value) => String((Array.isArray(value) ? value[0] : value) ?? '').trim()
const initialSpecialtyId = queryText(route.query.specialtyId)

const filters = reactive({
  keyword: queryText(route.query.keyword).slice(0, 100),
  specialtyId: /^\d+$/.test(initialSpecialtyId) ? initialSpecialtyId : ''
})
const doctors = ref([])
const specialtyOptions = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })

async function loadDoctors() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorsApi.list(cleanParams({ ...filters, page: page.value, size: size.value }))
    doctors.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    doctors.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return loadDoctors()
}

function changePage(next) {
  page.value = next
  loadDoctors()
}

onMounted(async () => {
  loadDoctors()
  try {
    const response = await catalogApi.specialties.list({ size: 100 })
    specialtyOptions.value = (response?.content ?? []).map((item) => ({ label: item.name, value: String(item.id) }))
  } catch {
    specialtyOptions.value = []
  }
})
</script>
