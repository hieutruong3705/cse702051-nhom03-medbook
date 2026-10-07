<template>
  <AdminLayout>
    <PageHeader
      title="Báo cáo"
      eyebrow="Quản trị"
      description="Ba báo cáo tổng hợp ở mức hành chính: lịch khám, doanh thu và dịch vụ khám. Không có nội dung bệnh án."
    />

    <form
      class="mt-4 grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[220px_180px_180px_auto]"
      @submit.prevent="loadReports"
    >
      <BaseSelect id="report-period" v-model="period" label="Khoảng thời gian" :options="periodOptions" />
      <BaseDatePicker id="report-from" :model-value="filters.from" label="Từ ngày" @update:model-value="setDate('from', $event)" />
      <BaseDatePicker id="report-to" :model-value="filters.to" label="Đến ngày" @update:model-value="setDate('to', $event)" />
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
            <DonutChart title="Tỷ lệ lịch khám theo trạng thái" :segments="appointmentShare" center-label="Tổng lịch" />
          </div>
          <div class="mt-6 border-t border-slate-100 pt-5">
            <component
              :is="isPeriodGroup(appointmentGroup) ? ColumnChart : BarChart"
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
            <BaseSelect id="report-revenue-group" v-model="revenueGroup" label="Nhóm theo" :options="periodGroupOptions" />
            <BaseButton variant="secondary" icon="fa-solid fa-file-csv" :loading="exporting === 'revenue'" @click="exportReport('revenue')">
              Xuất CSV doanh thu
            </BaseButton>
          </div>
        </div>
        <p v-if="errors.revenue" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">{{ errors.revenue }}</p>
        <template v-else>
          <dl class="mt-4 grid grid-cols-2 gap-3 text-sm">
            <div v-for="item in revenueCards" :key="item.label" class="rounded-md bg-slate-50 p-3">
              <dt class="text-slate-500">{{ item.label }}</dt>
              <dd class="mt-1 whitespace-nowrap text-xl font-bold text-slate-950">{{ item.value }}</dd>
            </div>
          </dl>
          <p class="mt-2 text-xs text-slate-500">Giá trị lập hóa đơn = Đã thu + Chưa thu. Hóa đơn đã hủy chỉ được đếm, không cộng tiền.</p>
          <div class="mt-4">
            <DonutChart
              title="Tỷ lệ đã thu và chưa thu"
              :segments="revenueShare"
              center-label="Lập hóa đơn"
              :center-value="compactCurrency(revenueReport.invoicedAmount)"
              :format="formatCurrency"
            />
          </div>
          <div class="mt-6 border-t border-slate-100 pt-5">
            <ColumnChart
              :title="`Giá trị hóa đơn theo ${periodLabel(revenueGroup)}`"
              :rows="revenueRows"
              :series="revenueSeries"
              :label-header="periodHeader(revenueGroup)"
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
          <div class="mt-5 grid gap-6 lg:grid-cols-2">
            <DonutChart
              title="Tỷ trọng thành tiền theo dịch vụ"
              :segments="serviceShare"
              center-label="Thành tiền"
              :center-value="compactCurrency(serviceReport.totalAmount)"
              :format="formatCurrency"
            />
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
import ColumnChart from '@/components/charts/ColumnChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { BaseButton, BaseDatePicker, BaseSelect, PageHeader } from '@/components/ui'
import { adminReportsApi } from '@/api/adminReports'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency } from '@/utils/formatters'
import { cleanParams, downloadBlob } from '@/utils/params'

// Khoảng thời gian chọn nhanh. LAST_30 để trống hai ngày: máy chủ tự lấy 30 ngày gần nhất.
const periodOptions = [
  { label: '30 ngày gần nhất', value: 'LAST_30' },
  { label: '7 ngày gần nhất', value: 'LAST_7' },
  { label: 'Tháng này', value: 'THIS_MONTH' },
  { label: 'Tháng trước', value: 'LAST_MONTH' },
  { label: 'Năm nay', value: 'THIS_YEAR' },
  { label: 'Tùy chọn (nhập hai ngày)', value: 'CUSTOM' }
]
// Giá trị groupBy đúng theo hợp đồng của máy chủ: cả hai báo cáo nhận DAY, MONTH, YEAR; lịch khám nhận thêm DOCTOR,
// SPECIALTY.
const periodGroupOptions = [
  { label: 'Ngày', value: 'DAY' },
  { label: 'Tháng', value: 'MONTH' },
  { label: 'Năm', value: 'YEAR' }
]
const appointmentGroupOptions = [
  ...periodGroupOptions,
  { label: 'Bác sĩ', value: 'DOCTOR' },
  { label: 'Chuyên khoa', value: 'SPECIALTY' }
]
const GROUP_LABELS = { DAY: 'ngày', MONTH: 'tháng', YEAR: 'năm', DOCTOR: 'bác sĩ', SPECIALTY: 'chuyên khoa' }
const GROUP_HEADERS = { DAY: 'Ngày', MONTH: 'Tháng', YEAR: 'Năm', DOCTOR: 'Bác sĩ', SPECIALTY: 'Chuyên khoa' }
const isPeriodGroup = (group) => ['DAY', 'MONTH', 'YEAR'].includes(group)
const periodLabel = (group) => GROUP_LABELS[group]
const periodHeader = (group) => GROUP_HEADERS[group]

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

const period = ref('LAST_30')
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
const compactNumber = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 })
const compactCurrency = (value) => `${compactNumber.format(Number(value || 0))} ₫`
const range = () => cleanParams({ from: filters.from, to: filters.to })

const isoDay = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

/** Hai ngày đầu và cuối của một khoảng chọn nhanh, tính theo ngày hiện tại trên máy người dùng. */
function periodRange(value) {
  const today = new Date()
  const year = today.getFullYear()
  const month = today.getMonth()
  switch (value) {
    case 'LAST_7':
      return [isoDay(new Date(year, month, today.getDate() - 6)), isoDay(today)]
    case 'THIS_MONTH':
      return [isoDay(new Date(year, month, 1)), isoDay(today)]
    case 'LAST_MONTH':
      return [isoDay(new Date(year, month - 1, 1)), isoDay(new Date(year, month, 0))]
    case 'THIS_YEAR':
      return [isoDay(new Date(year, 0, 1)), isoDay(today)]
    default:
      return ['', '']
  }
}

function setDate(field, value) {
  filters[field] = value
  period.value = 'CUSTOM'
}

const appointmentGroupLabel = computed(() => GROUP_LABELS[appointmentGroup.value])
const appointmentGroupHeader = computed(() => GROUP_HEADERS[appointmentGroup.value])

const appointmentCards = computed(() => [
  { label: 'Tổng lịch khám', value: appointmentReport.value.total ?? 0 },
  { label: 'Hoàn thành', value: appointmentReport.value.completed ?? 0 },
  { label: 'Đã hủy', value: appointmentReport.value.cancelled ?? 0 },
  { label: 'Tỷ lệ hủy', value: `${formatNumber(appointmentReport.value.cancellationRate)}%` }
])

// Thứ tự các phần trùng với thứ tự chuỗi của biểu đồ cột nên mỗi trạng thái giữ một màu ở cả hai biểu đồ.
const appointmentShare = computed(() => {
  const report = appointmentReport.value
  const completed = Number(report.completed || 0)
  const cancelled = Number(report.cancelled || 0)
  return [
    { key: 'completed', label: 'Hoàn thành', value: completed },
    { key: 'cancelled', label: 'Đã hủy', value: cancelled },
    { key: 'other', label: 'Đã đặt hoặc đang khám', value: Math.max(0, Number(report.total || 0) - completed - cancelled) }
  ]
})

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

const revenueShare = computed(() => [
  { key: 'collected', label: 'Đã thu', value: Number(revenueReport.value.collectedAmount || 0) },
  { key: 'unpaid', label: 'Chưa thu', value: Number(revenueReport.value.unpaidAmount || 0) }
])

const revenueRows = computed(() => (revenueReport.value.groups || []).map((group) => ({
  label: group.label,
  values: { collected: group.collectedAmount, unpaid: group.unpaidAmount }
})))

const serviceRows = computed(() => (serviceReport.value.services || []).map((row) => ({
  label: row.serviceName,
  values: { amount: row.amount }
})))

// Màu gắn với dịch vụ (xếp theo mã) chứ không theo thứ hạng, để đổi khoảng thời gian không làm dịch vụ đổi màu.
const serviceShare = computed(() => {
  const services = serviceReport.value.services || []
  const order = [...services].sort((a, b) => Number(a.serviceId ?? Infinity) - Number(b.serviceId ?? Infinity))
  return services.map((row) => ({
    key: String(row.serviceId ?? row.serviceName),
    label: row.serviceName,
    value: Number(row.amount || 0),
    colorIndex: order.indexOf(row)
  }))
})

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

// Chọn nhanh một khoảng thì điền hai ngày và tải lại ngay; "Tùy chọn" chờ người dùng nhập ngày rồi bấm Xem báo cáo.
watch(period, (value) => {
  if (value === 'CUSTOM') return
  const [from, to] = periodRange(value)
  filters.from = from
  filters.to = to
  loadReports()
})
watch(appointmentGroup, loadAppointments)
watch(revenueGroup, loadRevenue)
onMounted(loadReports)
</script>
