<template>
  <AdminLayout>
    <PageHeader
      title="Hóa đơn"
      eyebrow="Quản trị"
      description="Theo dõi hóa đơn, đánh dấu đã thu hoặc hủy. Không gọi hóa đơn đã lập là đã thanh toán trực tuyến."
    />

    <section class="space-y-4 p-4 sm:p-6">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-5" @submit.prevent="reload">
        <BaseDatePicker id="admin-invoice-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="admin-invoice-to" v-model="filters.to" label="Đến ngày" />
        <BaseSelect id="admin-invoice-status" v-model="filters.status" label="Trạng thái" :options="statusOptions" />
        <BaseInput id="admin-invoice-keyword" v-model="filters.keyword" label="Tìm kiếm" placeholder="Mã hóa đơn, bệnh nhân" />
        <BaseButton type="submit" variant="secondary" class="self-end">
          <i class="fa-solid fa-filter" aria-hidden="true"></i>
          Lọc
        </BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có hóa đơn">
        <template #cell-code="{ row }">
          <button type="button" class="font-semibold text-primary-700 hover:underline" @click="openDetails(row)">
            {{ row.invoiceCode || row.code || `#${row.id}` }}
          </button>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="invoiceStatus[row.status] || row.status" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap gap-2">
            <BaseButton type="button" size="sm" variant="secondary" :disabled="row.status !== 'UNPAID'" @click="confirmCollect(row)">
              Đã thu
            </BaseButton>
            <BaseButton type="button" size="sm" variant="danger" :disabled="row.status === 'VOID'" @click="confirmVoid(row)">
              Hủy
            </BaseButton>
          </div>
        </template>
      </DataTable>

      <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
    </section>

    <FormModal v-model="detailsOpen" title="Chi tiết hóa đơn">
      <div v-if="selected" class="space-y-4">
        <dl class="grid gap-3 text-sm md:grid-cols-2">
          <div><dt class="text-slate-500">Mã hóa đơn</dt><dd class="font-semibold">{{ selected.invoiceCode || selected.code || `#${selected.id}` }}</dd></div>
          <div><dt class="text-slate-500">Trạng thái</dt><dd><StatusBadge :value="selected.status" :label="invoiceStatus[selected.status] || selected.status" /></dd></div>
          <div><dt class="text-slate-500">Tổng giá trị hóa đơn</dt><dd class="font-semibold">{{ formatCurrency(selected.totalAmount) }}</dd></div>
          <div><dt class="text-slate-500">Đã thu</dt><dd class="font-semibold">{{ selected.status === 'PAID' ? formatCurrency(selected.totalAmount) : formatCurrency(0) }}</dd></div>
        </dl>
        <DataTable :columns="itemColumns" :rows="selected.items || selected.invoiceItems || []" empty-text="Chưa có dòng hóa đơn" />
      </div>
      <template #footer>
        <BaseButton type="button" variant="secondary" @click="detailsOpen = false">Đóng</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="confirmMode === 'collect' ? 'Đánh dấu đã thu?' : 'Hủy hóa đơn?'"
      :message="confirmMode === 'collect' ? 'Thao tác này chỉ ghi nhận đã thu tại quầy/admin.' : 'Hóa đơn bị hủy sẽ không tính vào doanh thu đã thu.'"
      :danger="confirmMode === 'void'"
      confirm-text="Xác nhận"
      @confirm="runAction"
    />
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import {
  BaseButton,
  BaseDatePicker,
  BaseInput,
  BaseSelect,
  ConfirmDialog,
  DataTable,
  FormModal,
  PageHeader,
  Pagination,
  StatusBadge
} from '@/components/ui'
import { invoicesApi } from '@/api/invoices'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatDate } from '@/utils/formatters'
import { invoiceStatus } from '@/utils/statusLabels'

const statusOptions = [
  { label: 'Tất cả', value: '' },
  { label: 'Chưa thu', value: 'UNPAID' },
  { label: 'Đã thu', value: 'PAID' },
  { label: 'Đã hủy', value: 'VOID' }
]

const columns = [
  { key: 'code', label: 'Hóa đơn' },
  { key: 'patientName', label: 'Bệnh nhân', formatter: (_, row) => row.patientName || row.patient?.fullName || '-' },
  { key: 'issuedAt', label: 'Ngày lập', formatter: formatDate },
  { key: 'totalAmount', label: 'Tổng giá trị', formatter: formatCurrency },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const itemColumns = [
  { key: 'serviceName', label: 'Dịch vụ', formatter: (_, row) => row.serviceName || row.service?.name || '-' },
  { key: 'quantity', label: 'SL' },
  { key: 'unitPrice', label: 'Đơn giá', formatter: formatCurrency },
  { key: 'lineTotal', label: 'Thành tiền', formatter: formatCurrency }
]

const filters = reactive({ from: '', to: '', status: '', keyword: '' })
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })
const { success, error: toastError } = useToast()
const rows = ref([])
const loading = ref(false)
const error = ref('')
const detailsOpen = ref(false)
const confirmOpen = ref(false)
const confirmMode = ref('collect')
const selected = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await invoicesApi.listAdmin({ ...filters, page: page.value, size: size.value })
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

async function openDetails(row) {
  selected.value = row
  detailsOpen.value = true
  try {
    selected.value = await invoicesApi.get(row.id)
  } catch {
    // Row summary is still enough for the modal.
  }
}

function confirmCollect(row) {
  selected.value = row
  confirmMode.value = 'collect'
  confirmOpen.value = true
}

function confirmVoid(row) {
  selected.value = row
  confirmMode.value = 'void'
  confirmOpen.value = true
}

async function runAction() {
  if (!selected.value) return
  try {
    if (confirmMode.value === 'collect') await invoicesApi.collect(selected.value.id)
    else await invoicesApi.void(selected.value.id)
    success(confirmMode.value === 'collect' ? 'Đã đánh dấu đã thu' : 'Đã hủy hóa đơn')
    load()
  } catch (err) {
    toastError(apiErrorMessage(err))
  }
}

onMounted(load)
</script>
