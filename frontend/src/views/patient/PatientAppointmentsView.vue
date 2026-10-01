<template>
  <PatientLayout>
    <PageHeader title="Lịch hẹn của tôi" eyebrow="Bệnh nhân" description="Theo dõi lịch khám theo trạng thái và khoảng ngày. Chỉ dùng danh tính từ JWT." />

    <section class="space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-4" @submit.prevent="reload">
        <BaseDatePicker id="patient-appointment-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="patient-appointment-to" v-model="filters.to" label="Đến ngày" />
        <BaseSelect id="patient-appointment-status" v-model="filters.status" label="Trạng thái" :options="statusOptions" />
        <BaseButton type="submit" variant="secondary" class="self-end">
          <i class="fa-solid fa-filter" aria-hidden="true"></i>
          Lọc
        </BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có lịch hẹn">
        <template #cell-doctor="{ row }">
          <div>
            <p class="font-semibold text-slate-900">{{ doctorName(row) }}</p>
            <p class="text-xs text-slate-500">{{ row.specialtyName || row.doctor?.specialty?.name || '-' }}</p>
          </div>
        </template>
        <template #cell-time="{ row }">
          {{ appointmentDate(row) }} - {{ appointmentTime(row) }}
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="appointmentStatus[row.status] || row.status" />
        </template>
        <template #cell-actions="{ row }">
          <RouterLink class="text-sm font-semibold text-primary-700 hover:underline" :to="`/patient/appointments/${row.id}`">
            Chi tiết
          </RouterLink>
        </template>
      </DataTable>

      <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
    </section>
  </PatientLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseDatePicker, BaseSelect, DataTable, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { appointmentsApi } from '@/api/appointments'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatTime } from '@/utils/formatters'
import { appointmentStatus } from '@/utils/statusLabels'

const statusOptions = [
  { label: 'Tất cả', value: '' },
  { label: 'Đã đặt', value: 'BOOKED' },
  { label: 'Đang khám', value: 'IN_PROGRESS' },
  { label: 'Hoàn thành', value: 'COMPLETED' },
  { label: 'Đã hủy', value: 'CANCELLED' }
]

const columns = [
  { key: 'doctor', label: 'Bác sĩ' },
  { key: 'serviceName', label: 'Dịch vụ', formatter: (value, row) => value || row.service?.name || '-' },
  { key: 'time', label: 'Thời gian' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const filters = reactive({ from: '', to: '', status: '' })
const rows = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })

function slot(row) {
  return row.slot || row.appointmentSlot || {}
}

function doctorName(row) {
  return row.doctorName || row.doctor?.fullName || row.doctor?.user?.fullName || 'Bác sĩ'
}

function appointmentDate(row) {
  return formatDate(row.date || row.appointmentDate || slot(row).slotDate || row.startTime)
}

function appointmentTime(row) {
  const currentSlot = slot(row)
  const start = currentSlot.startTime || row.startTime
  const end = currentSlot.endTime || row.endTime
  return [formatTime(start), formatTime(end)].filter(Boolean).join(' - ') || '-'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await appointmentsApi.mine({ ...filters, page: page.value, size: size.value })
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
