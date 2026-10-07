<template>
  <DoctorLayout>
    <PageHeader title="Bệnh nhân phụ trách" eyebrow="Bác sĩ" description="Chỉ gồm bệnh nhân đã có lịch hẹn hoặc lần khám với bạn." />

    <section class="mt-4 space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_auto]" @submit.prevent="reload">
        <BaseInput id="doctor-patient-keyword" v-model="keyword" label="Tìm kiếm" placeholder="Tên, mã bệnh nhân, số điện thoại" />
        <BaseButton type="submit" variant="secondary" class="self-end" icon="fa-solid fa-magnifying-glass">Tìm</BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có bệnh nhân phù hợp">
        <template #cell-name="{ row }">
          <RouterLink class="font-semibold text-primary-700 hover:underline" :to="`/doctor/patients/${row.id}`">{{ row.fullName || '-' }}</RouterLink>
          <p class="text-xs text-slate-500">{{ row.patientCode || '' }}</p>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </section>
  </DoctorLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { BaseButton, BaseInput, DataTable, PageHeader, Pagination } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { patientsApi } from '@/api/patients'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate } from '@/utils/formatters'
import { cleanParams } from '@/utils/params'

const genderLabels = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' }
const columns = [
  { key: 'name', label: 'Bệnh nhân' },
  { key: 'dateOfBirth', label: 'Ngày sinh', formatter: formatDate },
  { key: 'genderCode', label: 'Giới tính', formatter: (value) => genderLabels[value] || value || '-' },
  { key: 'phone', label: 'Điện thoại', formatter: (value) => value || '-' }
]

const keyword = ref('')
const rows = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await patientsApi.list(cleanParams({ keyword: keyword.value, page: page.value, size: size.value }))
    rows.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    rows.value = []
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

onMounted(load)
</script>
