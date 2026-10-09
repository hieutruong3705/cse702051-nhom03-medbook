<template>
  <ReceptionistLayout>
    <div class="space-y-6">
      <PageHeader title="Bệnh nhân" eyebrow="Quầy lễ tân" description="Tìm hồ sơ bệnh nhân, tạo hồ sơ mới và chuyển nhanh sang đặt lịch hộ.">
        <template #actions>
          <BaseButton icon="fa-solid fa-user-plus" @click="createOpen = true">Thêm bệnh nhân</BaseButton>
        </template>
      </PageHeader>

      <form class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_auto]" @submit.prevent="search">
        <BaseInput id="patient-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Họ tên, mã bệnh nhân, điện thoại" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass">Tìm</BaseButton>
        </div>
      </form>

      <section v-if="error" class="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p class="text-sm font-medium text-red-700">{{ error }}</p>
        <BaseButton class="mt-3" variant="secondary" icon="fa-solid fa-rotate-right" @click="load">Thử lại</BaseButton>
      </section>

      <DataTable v-else :columns="columns" :rows="patients" :loading="loading" empty-text="Chưa có bệnh nhân phù hợp">
        <template #cell-fullName="{ row }">
          <div class="font-semibold text-slate-950">{{ row.fullName }}</div>
          <div class="text-xs text-slate-500">{{ row.patientCode }}</div>
        </template>
        <template #cell-dateOfBirth="{ value }">{{ formatDate(value) }}</template>
        <template #cell-genderCode="{ value }">{{ genderLabels[value] || value }}</template>
        <template #cell-hasAccount="{ value }">{{ value ? 'Có' : 'Không' }}</template>
        <template #cell-actions="{ row }">
          <RouterLink :to="`/receptionist/booking?patientId=${row.id}`" class="inline-flex min-h-10 items-center justify-center gap-2 rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2">
            <i class="fa-solid fa-calendar-plus" aria-hidden="true"></i>
            Đặt lịch
          </RouterLink>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </div>

    <PatientFormModal v-model="createOpen" @created="handleCreated" />
  </ReceptionistLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { BaseButton, BaseInput, DataTable, PageHeader, Pagination } from '@/components/ui'
import ReceptionistLayout from '@/layouts/ReceptionistLayout.vue'
import PatientFormModal from './PatientFormModal.vue'
import { receptionApi } from '@/api/reception'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams } from '@/utils/params'
import { formatDate } from '@/utils/formatters'

const genderLabels = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' }
const columns = [
  { key: 'patientCode', label: 'Mã', formatter: (value) => value || '-' },
  { key: 'fullName', label: 'Họ tên' },
  { key: 'dateOfBirth', label: 'Ngày sinh' },
  { key: 'genderCode', label: 'Giới tính' },
  { key: 'phone', label: 'Điện thoại' },
  { key: 'hasAccount', label: 'Tài khoản' },
  { key: 'actions', label: 'Thao tác' }
]

const filters = reactive({ keyword: '' })
const patients = ref([])
const loading = ref(false)
const error = ref('')
const createOpen = ref(false)
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await receptionApi.patients.list(cleanParams({ keyword: filters.keyword, page: page.value, size: size.value }))
    patients.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    patients.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function search() {
  reset()
  load()
}

function changePage(next) {
  page.value = next
  load()
}

function handleCreated(patient) {
  patients.value = [patient, ...patients.value]
}

onMounted(load)
</script>
