<template>
  <DoctorLayout>
    <PageHeader title="Bệnh nhân phụ trách" eyebrow="Bác sĩ" description="Danh sách bệnh nhân trong phạm vi được backend cho phép." />

    <section class="space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_auto]" @submit.prevent="reload">
        <BaseInput id="doctor-patient-keyword" v-model="keyword" label="Tìm kiếm" placeholder="Tên, mã bệnh nhân, số điện thoại" />
        <BaseButton type="submit" variant="secondary" class="self-end">Tìm</BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có bệnh nhân">
        <template #cell-name="{ row }">
          <div>
            <p class="font-semibold text-slate-900">{{ row.fullName || row.user?.fullName || row.name || '-' }}</p>
            <p class="text-xs text-slate-500">{{ row.patientCode || row.code || '' }}</p>
          </div>
        </template>
      </DataTable>

      <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
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

const columns = [
  { key: 'name', label: 'Bệnh nhân' },
  { key: 'dateOfBirth', label: 'Ngày sinh', formatter: formatDate },
  { key: 'genderCode', label: 'Giới tính', formatter: (value) => value || '-' },
  { key: 'phone', label: 'Điện thoại', formatter: (value, row) => value || row.user?.phone || '-' }
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
    const response = await patientsApi.list({ keyword: keyword.value, page: page.value, size: size.value })
    rows.value = response.content ?? response.items ?? response
    setPageResponse(response)
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  load()
}

function changePage(next) {
  page.value = next
  load()
}

onMounted(load)
</script>
