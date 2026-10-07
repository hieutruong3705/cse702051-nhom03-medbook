<template>
  <AdminLayout>
    <PageHeader title="Theo dõi lịch hẹn" eyebrow="Quản trị" description="Chỉ hiển thị thông tin hành chính, không mở nội dung lâm sàng." />

    <section class="space-y-4 p-4 sm:p-6">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-5" @submit.prevent="reload">
        <BaseDatePicker id="admin-appointment-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="admin-appointment-to" v-model="filters.to" label="Đến ngày" />
        <BaseInput id="admin-appointment-doctor" v-model="filters.doctorId" label="Mã bác sĩ" type="number" />
        <BaseSelect id="admin-appointment-status" v-model="filters.status" label="Trạng thái" :options="statusOptions" />
        <BaseButton type="submit" variant="secondary" class="self-end">
          <i class="fa-solid fa-filter" aria-hidden="true"></i>
          Lọc
        </BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có lịch hẹn">
        <template #cell-time="{ row }">
          <div>
            <p class="font-medium text-slate-900">{{ appointmentDate(row) }}</p>
            <p class="text-xs text-slate-500">{{ appointmentTime(row) }}</p>
          </div>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="appointmentStatus[row.status] || row.status" />
        </template>
      </DataTable>

      <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
    </section>
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, DataTable, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import { appointmentsApi } from '@/api/appointments'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatTime } from '@/utils/formatters'
import { cleanParams } from '@/utils/params'
import { appointmentStatus } from '@/utils/statusLabels'

const statusOptions = [
  { label: 'Tất cả', value: '' },
  { label: 'Đã đặt', value: 'BOOKED' },
  { label: 'Đang khám', value: 'IN_PROGRESS' },
  { label: 'Hoàn thành', value: 'COMPLETED' },
  { label: 'Đã hủy', value: 'CANCELLED' }
]

const columns = [
  { key: 'id', label: 'Mã lịch', formatter: (value) => `#${value}` },
  { key: 'patientName', label: 'Bệnh nhân', formatter: (_, row) => row.patientName || row.patient?.fullName || row.patient?.name || '-' },
  { key: 'doctorName', label: 'Bác sĩ', formatter: (_, row) => row.doctorName || row.doctor?.fullName || row.doctor?.user?.fullName || '-' },
  { key: 'time', label: 'Thời gian' },
  { key: 'status', label: 'Trạng thái' }
]

const filters = reactive({ from: '', to: '', doctorId: '', status: '' })
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })
const rows = ref([])
const loading = ref(false)
const error = ref('')

function slot(row) {
  return row.slot || row.appointmentSlot || {}
}

function appointmentDate(row) {
  return formatDate(row.appointmentDate || row.date || slot(row).slotDate || row.startTime)
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
    const response = await appointmentsApi.listAdmin(cleanParams({ ...filters, page: page.value, size: size.value }))
    rows.value = response?.content ?? []
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
