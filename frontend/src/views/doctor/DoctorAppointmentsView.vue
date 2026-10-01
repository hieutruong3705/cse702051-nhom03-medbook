<template>
  <DoctorLayout>
    <PageHeader title="Lịch khám" eyebrow="Bác sĩ" description="Theo dõi lịch khám của bạn và bắt đầu khám khi đến lượt." />

    <section class="space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-4" @submit.prevent="reload">
        <BaseDatePicker id="doctor-appointment-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="doctor-appointment-to" v-model="filters.to" label="Đến ngày" />
        <BaseSelect id="doctor-appointment-status" v-model="filters.status" label="Trạng thái" :options="statusOptions" />
        <BaseButton type="submit" variant="secondary" class="self-end">Lọc</BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có lịch khám">
        <template #cell-patient="{ row }">
          <div>
            <p class="font-semibold text-slate-900">{{ patientName(row) }}</p>
            <p class="text-xs text-slate-500">{{ row.notes || 'Không có ghi chú' }}</p>
          </div>
        </template>
        <template #cell-time="{ row }">
          {{ appointmentDate(row) }} - {{ appointmentTime(row) }}
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="appointmentStatus[row.status] || row.status" />
        </template>
        <template #cell-actions="{ row }">
          <BaseButton type="button" size="sm" :disabled="row.status !== 'BOOKED' || startingId === row.id" @click="startEncounter(row)">
            Bắt đầu khám
          </BaseButton>
        </template>
      </DataTable>

      <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
    </section>
  </DoctorLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BaseButton, BaseDatePicker, BaseSelect, DataTable, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { appointmentsApi } from '@/api/appointments'
import { encountersApi } from '@/api/encounters'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
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
  { key: 'patient', label: 'Bệnh nhân' },
  { key: 'serviceName', label: 'Dịch vụ', formatter: (value, row) => value || row.service?.name || '-' },
  { key: 'time', label: 'Thời gian' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const router = useRouter()
const { error: toastError } = useToast()
const filters = reactive({ from: '', to: '', status: '' })
const rows = ref([])
const loading = ref(false)
const error = ref('')
const startingId = ref(null)
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })

function slot(row) {
  return row.slot || row.appointmentSlot || {}
}

function patientName(row) {
  return row.patientName || row.patient?.fullName || row.patient?.user?.fullName || 'Bệnh nhân'
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

async function startEncounter(row) {
  startingId.value = row.id
  try {
    const encounter = await encountersApi.create({ appointmentId: row.id })
    router.push(`/doctor/encounters/${encounter.id}`)
  } catch (err) {
    toastError(apiErrorMessage(err))
  } finally {
    startingId.value = null
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
