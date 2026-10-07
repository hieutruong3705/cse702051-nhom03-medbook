<template>
  <AdminLayout>
    <PageHeader
      title="Báo cáo"
      eyebrow="Quản trị"
      description="Ba báo cáo tổng hợp ở mức hành chính: lịch khám, doanh thu và dịch vụ khám. Không có nội dung bệnh án."
    />

    <form class="mt-4 grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[180px_180px_auto]" @submit.prevent="loadReports">
      <BaseDatePicker id="report-from" v-model="filters.from" label="Từ ngày" hint="Bỏ trống: 30 ngày gần nhất" />
      <BaseDatePicker id="report-to" v-model="filters.to" label="Đến ngày" />
      <div class="flex items-start pt-7">
        <BaseButton type="submit" :loading="loading" icon="fa-solid fa-chart-column">Xem báo cáo</BaseButton>
      </div>
    </form>

    <div class="mt-4 grid gap-4 xl:grid-cols-2">
      <!-- 1. Lịch khám -->
      <section class="rounded-md border border-slate-200 bg-white p-5" aria-labelledby="report-appointments-title">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <h2 id="report-appointments-title" class="font-semibold text-slate-950">Lịch khám</h2>
          <div class="flex flex-wrap items-end gap-2">
            <BaseSelect id="report-appointment-group" v-model="appointmentGroup" label="Nhóm theo" :options="appointmentGroupOptions" />
            <BaseButton variant="secondary" icon="fa-solid fa-file-csv" :loading="exporting === 'appointments'" @click="exportReport('appointments')">
              Xuất CSV lịch khám
            </BaseButton>
          </div>
        </div>
        <p v-if="errors.appointments" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">{{ errors.appointments }}</p>
        <template v-else>
          <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-4">
            <div v-for="item in appointmentCards" :key="item.label" class="rounded-md bg-slate-50 p-3">
              <dt class="text-slate-500">{{ item.label }}</dt>
              <dd class="mt-1 text-2xl font-bold text-slate-950">{{ item.value }}</dd>
            </div>
          </dl>
          <div class="mt-5">
            <BarChart
              :title="`Số lịch khám theo ${appointmentGroupLabel}`"
              :rows="appointmentRows"
              :series="appointmentSeries"
              :label-header="appointmentGroupHeader"
            />
          </div>
        </template>
      </section>

      <!-- 2. Doanh thu -->
      <section class="rounded-md border border-slate-200 bg-white p-5" aria-labelledby="report-revenue-title">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <h2 id="report-revenue-title" class="font-semibold text-slate-950">Doanh thu</h2>
          <div class="flex flex-wrap items-end gap-2">
            <BaseSelect id="report-revenue-group" v-model="revenueGroup" label="Nhóm theo" :options="revenueGroupOptions" />
            <BaseButton variant="secondary" icon="fa-solid fa-file-csv" :loading="exporting === 'revenue'" @click="exportReport('revenue')">
              Xuất CSV doanh thu
            </BaseButton>
          </div>
        </div>
        <p v-if="errors.revenue" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">{{ errors.revenue }}</p>
        <template v-else>
          <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-4">
            <div v-for="item in revenueCards" :key="item.label" class="rounded-md bg-slate-50 p-3">
              <dt class="text-slate-500">{{ item.label }}</dt>
              <dd class="mt-1 text-xl font-bold text-slate-950">{{ item.value }}</dd>
            </div>
          </dl>
          <p class="mt-2 text-xs text-slate-500">Giá trị lập hóa đơn = Đã thu + Chưa thu. Hóa đơn đã hủy chỉ được đếm, không cộng tiền.</p>
          <div class="mt-4">
            <BarChart
              :title="`Giá trị hóa đơn theo ${revenueGroup === 'MONTH' ? 'tháng' : 'ngày'}`"
              :rows="revenueRows"
              :series="revenueSeries"
              :label-header="revenueGroup === 'MONTH' ? 'Tháng' : 'Ngày'"
              :format="formatCurrency"
            />
          </div>
        </template>
      </section>

      <!-- 3. Dịch vụ khám -->
      <section class="rounded-md border border-slate-200 bg-white p-5 xl:col-span-2" aria-labelledby="report-services-title">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 id="report-services-title" class="font-semibold text-slate-950">Dịch vụ khám</h2>
            <p class="mt-1 text-xs text-slate-500">Tính trên dòng hóa đơn của các hóa đơn không bị hủy, trước giảm giá của cả hóa đơn.</p>
          </div>
          <BaseButton variant="secondary" icon="fa-solid fa-file-csv" :loading="exporting === 'services'" @click="exportReport('services')">
            Xuất CSV dịch vụ
          </BaseButton>
        </div>
        <p v-if="errors.services" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">{{ errors.services }}</p>
        <template v-else>
          <dl class="mt-4 grid gap-3 text-sm sm:grid-cols-3">
            <div class="rounded-md bg-slate-50 p-3">
              <dt class="text-slate-500">Số dịch vụ có phát sinh</dt>
              <dd class="mt-1 text-2xl font-bold text-slate-950">{{ serviceReport.services?.length ?? 0 }}</dd>
            </div>
            <div class="rounded-md bg-slate-50 p-3">
              <dt class="text-slate-500">Tổng số lượng</dt>
              <dd class="mt-1 text-2xl font-bold text-slate-950">{{ formatNumber(serviceReport.totalQuantity) }}</dd>
            </div>
            <div class="rounded-md bg-slate-50 p-3">
              <dt class="text-slate-500">Tổng thành tiền</dt>
              <dd class="mt-1 text-2xl font-bold text-slate-950">{{ formatCurrency(serviceReport.totalAmount) }}</dd>
            </div>
          </dl>
          <div class="mt-5">
            <BarChart
              title="Thành tiền theo dịch vụ"
              :rows="serviceRows"
              :series="serviceSeries"
              label-header="Dịch vụ"
              :format="formatCurrency"
            />
          </div>
        </template>
      </section>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import BarChart from '@/components/charts/BarChart.vue'
import { BaseButton, BaseDatePicker, BaseSelect, PageHeader } from '@/components/ui'
import { adminReportsApi } from '@/api/adminReports'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency } from '@/utils/formatters'
import { cleanParams, downloadBlob } from '@/utils/params'

// Giá trị groupBy đúng theo hợp đồng của máy chủ: lịch khám nhận DAY, DOCTOR, SPECIALTY; doanh thu nhận DAY, MONTH.
const appointmentGroupOptions = [
  { label: 'Ngày', value: 'DAY' },
  { label: 'Bác sĩ', value: 'DOCTOR' },
  { label: 'Chuyên khoa', value: 'SPECIALTY' }
]
const revenueGroupOptions = [
  { label: 'Ngày', value: 'DAY' },
  { label: 'Tháng', value: 'MONTH' }
]
const appointmentSeries = [
  { key: 'completed', label: 'Hoàn thành' },
  { key: 'cancelled', label: 'Đã hủy' },
  { key: 'other', label: 'Đã đặt hoặc đang khám' }
]
const revenueSeries = [
  { key: 'collected', label: 'Đã thu' },
  { key: 'unpaid', label: 'Chưa thu' }
]
const serviceSeries = [{ key: 'amount', label: 'Thành tiền' }]

const filters = reactive({ from: '', to: '' })
const appointmentGroup = ref('DAY')
const revenueGroup = ref('DAY')
const loading = ref(false)
const exporting = ref('')
const appointmentReport = ref({})
const revenueReport = ref({})
const serviceReport = ref({})
const errors = reactive({ appointments: '', revenue: '', services: '' })
const { success, error: toastError } = useToast()

const formatNumber = (value) => new Intl.NumberFormat('vi-VN').format(Number(value || 0))
const range = () => cleanParams({ from: filters.from, to: filters.to })

const appointmentGroupLabel = computed(() => ({ DAY: 'ngày', DOCTOR: 'bác sĩ', SPECIALTY: 'chuyên khoa' }[appointmentGroup.value]))
const appointmentGroupHeader = computed(() => ({ DAY: 'Ngày', DOCTOR: 'Bác sĩ', SPECIALTY: 'Chuyên khoa' }[appointmentGroup.value]))

const appointmentCards = computed(() => [
  { label: 'Tổng lịch khám', value: appointmentReport.value.total ?? 0 },
  { label: 'Hoàn thành', value: appointmentReport.value.completed ?? 0 },
  { label: 'Đã hủy', value: appointmentReport.value.cancelled ?? 0 },
  { label: 'Tỷ lệ hủy', value: `${formatNumber(appointmentReport.value.cancellationRate)}%` }
])

const appointmentRows = computed(() => (appointmentReport.value.groups || []).map((group) => ({
  label: group.label,
  values: {
    completed: group.completed,
    cancelled: group.cancelled,
    other: Math.max(0, group.total - group.completed - group.cancelled)
  }
})))

const revenueCards = computed(() => [
  { label: 'Giá trị lập hóa đơn', value: formatCurrency(revenueReport.value.invoicedAmount) },
  { label: 'Đã thu', value: formatCurrency(revenueReport.value.collectedAmount) },
  { label: 'Chưa thu', value: formatCurrency(revenueReport.value.unpaidAmount) },
  { label: 'Hóa đơn đã hủy', value: revenueReport.value.voidCount ?? 0 }
])

const revenueRows = computed(() => (revenueReport.value.groups || []).map((group) => ({
  label: group.label,
  values: { collected: group.collectedAmount, unpaid: group.unpaidAmount }
})))

const serviceRows = computed(() => (serviceReport.value.services || []).map((row) => ({
  label: row.serviceName,
  values: { amount: row.amount }
})))

async function loadOne(key, request, target) {
  errors[key] = ''
  try {
    target.value = (await request()) || {}
  } catch (err) {
    target.value = {}
    errors[key] = apiErrorMessage(err)
  }
}

const loadAppointments = () =>
  loadOne('appointments', () => adminReportsApi.appointments({ ...range(), groupBy: appointmentGroup.value }), appointmentReport)
const loadRevenue = () =>
  loadOne('revenue', () => adminReportsApi.revenue({ ...range(), groupBy: revenueGroup.value }), revenueReport)
const loadServices = () => loadOne('services', () => adminReportsApi.services(range()), serviceReport)

async function loadReports() {
  loading.value = true
  await Promise.all([loadAppointments(), loadRevenue(), loadServices()])
  loading.value = false
}

async function exportReport(kind) {
  if (exporting.value) return
  exporting.value = kind
  const stamp = `${filters.from || 'tu-dau'}_${filters.to || 'den-nay'}`
  try {
    if (kind === 'appointments') {
      downloadBlob(await adminReportsApi.exportAppointments(range()), `medbook-lich-kham-${stamp}.csv`)
    } else if (kind === 'revenue') {
      downloadBlob(await adminReportsApi.exportRevenue({ ...range(), groupBy: revenueGroup.value }), `medbook-doanh-thu-${stamp}.csv`)
    } else {
      downloadBlob(await adminReportsApi.exportServices(range()), `medbook-dich-vu-${stamp}.csv`)
    }
    success('Đã xuất tệp CSV')
  } catch (err) {
    toastError(apiErrorMessage(err))
  } finally {
    exporting.value = ''
  }
}

watch(appointmentGroup, loadAppointments)
watch(revenueGroup, loadRevenue)
onMounted(loadReports)
</script>
