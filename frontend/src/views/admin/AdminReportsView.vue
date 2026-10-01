<template>
  <AdminLayout>
    <PageHeader title="Báo cáo" eyebrow="Quản trị" description="Tổng hợp lịch hẹn và doanh thu ở mức hành chính." />

    <section class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[160px_160px_180px_auto]">
      <BaseDatePicker id="report-from" v-model="filters.from" label="Từ ngày" />
      <BaseDatePicker id="report-to" v-model="filters.to" label="Đến ngày" />
      <BaseSelect id="report-group" v-model="filters.groupBy" label="Nhóm theo" :options="groupOptions" />
      <div class="flex items-end">
        <BaseButton class="w-full" :loading="loading" icon="fa-solid fa-chart-column" @click="loadReports">Xem báo cáo</BaseButton>
      </div>
    </section>

    <section class="mt-4 grid gap-4 xl:grid-cols-2">
      <article class="rounded-md border border-slate-200 bg-white p-5">
        <h2 class="font-semibold text-slate-950">Lịch hẹn</h2>
        <p v-if="appointmentError" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">{{ appointmentError }}</p>
        <dl v-else class="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div v-for="item in appointmentCards" :key="item.label" class="rounded-md bg-slate-50 p-3">
            <dt class="text-slate-500">{{ item.label }}</dt>
            <dd class="mt-1 text-2xl font-bold text-slate-950">{{ item.value }}</dd>
          </div>
        </dl>
      </article>

      <article class="rounded-md border border-slate-200 bg-white p-5">
        <h2 class="font-semibold text-slate-950">Doanh thu</h2>
        <p v-if="revenueError" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">{{ revenueError }}</p>
        <dl v-else class="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div v-for="item in revenueCards" :key="item.label" class="rounded-md bg-slate-50 p-3">
            <dt class="text-slate-500">{{ item.label }}</dt>
            <dd class="mt-1 text-2xl font-bold text-slate-950">{{ item.currency ? formatCurrency(item.value) : item.value }}</dd>
          </div>
        </dl>
      </article>
    </section>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { BaseButton, BaseDatePicker, BaseSelect, PageHeader } from '@/components/ui'
import { adminReportsApi } from '@/api/adminReports'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency } from '@/utils/formatters'

const groupOptions = [
  { label: 'Ngày', value: 'day' },
  { label: 'Tuần', value: 'week' },
  { label: 'Tháng', value: 'month' }
]

const filters = reactive({ from: '', to: '', groupBy: 'day' })
const loading = ref(false)
const appointmentReport = ref({})
const revenueReport = ref({})
const appointmentError = ref('')
const revenueError = ref('')

const appointmentCards = computed(() => [
  { label: 'Tổng lịch hẹn', value: appointmentReport.value.totalAppointments ?? appointmentReport.value.total ?? '--' },
  { label: 'Đã đặt', value: appointmentReport.value.bookedCount ?? appointmentReport.value.booked ?? '--' },
  { label: 'Hoàn thành', value: appointmentReport.value.completedCount ?? appointmentReport.value.completed ?? '--' },
  { label: 'Đã hủy', value: appointmentReport.value.cancelledCount ?? appointmentReport.value.cancelled ?? '--' }
])

const revenueCards = computed(() => [
  { label: 'Giá trị lập hóa đơn', value: revenueReport.value.invoicedAmount ?? revenueReport.value.totalAmount ?? 0, currency: true },
  { label: 'Đã thu', value: revenueReport.value.collectedAmount ?? revenueReport.value.paidAmount ?? 0, currency: true },
  { label: 'Chưa thu', value: revenueReport.value.unpaidAmount ?? 0, currency: true },
  { label: 'Hóa đơn hủy', value: revenueReport.value.voidCount ?? revenueReport.value.voidedCount ?? '--' }
])

async function loadReports() {
  loading.value = true
  appointmentError.value = ''
  revenueError.value = ''
  try {
    appointmentReport.value = await adminReportsApi.appointments({ ...filters })
  } catch (err) {
    appointmentReport.value = {}
    appointmentError.value = apiErrorMessage(err)
  }
  try {
    revenueReport.value = await adminReportsApi.revenue({ ...filters })
  } catch (err) {
    revenueReport.value = {}
    revenueError.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadReports)
</script>
