<template>
  <ReceptionistLayout>
    <div class="space-y-6">
      <PageHeader title="Hóa đơn" eyebrow="Quầy lễ tân" description="Tìm hóa đơn, xem chi tiết dòng dịch vụ và ghi nhận đã thu tiền." />

      <form class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 lg:grid-cols-5" @submit.prevent="reload">
        <BaseSelect id="invoice-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <BaseDatePicker id="invoice-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="invoice-to" v-model="filters.to" label="Đến ngày" />
        <BaseInput id="invoice-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Mã hóa đơn, bệnh nhân" />
        <div class="flex items-end">
          <BaseButton type="submit" :loading="loading" icon="fa-solid fa-filter">Lọc</BaseButton>
        </div>
      </form>

      <section v-if="error" class="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p class="text-sm font-medium text-red-700">{{ error }}</p>
        <BaseButton class="mt-3" variant="secondary" icon="fa-solid fa-rotate-right" @click="load">Thử lại</BaseButton>
      </section>

      <DataTable v-else :columns="columns" :rows="invoices" :loading="loading" empty-text="Chưa có hóa đơn phù hợp">
        <template #cell-invoiceCode="{ row }">
          <button type="button" class="font-semibold text-primary-700 hover:text-primary-800" @click="openDetail(row)">{{ row.invoiceCode }}</button>
        </template>
        <template #cell-patient="{ row }">
          <div class="font-semibold text-slate-950">{{ row.patientName }}</div>
          <div class="text-xs text-slate-500">{{ row.patientCode }}</div>
        </template>
        <template #cell-issuedAt="{ value }">{{ formatDateTime(value) }}</template>
        <template #cell-totalAmount="{ value }">
          <span class="block text-right font-semibold text-slate-950">{{ formatCurrency(value) }}</span>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status === 'VOID' ? 'CANCELLED' : row.status" :label="invoiceStatus[row.status] || row.status" />
        </template>
        <template #cell-actions="{ row }">
          <BaseButton v-if="row.status === 'UNPAID'" :aria-label="`Thu tiền ${row.invoiceCode}`" @click="openCollect(row)">Thu tiền</BaseButton>
          <span v-else class="text-sm text-slate-500">Không có</span>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </div>

    <FormModal v-model="detailOpen" :title="`Chi tiết hóa đơn ${selectedInvoice?.invoiceCode || ''}`">
      <div v-if="detailLoading" class="py-6">
        <LoadingSpinner label="Đang tải dòng hóa đơn" />
      </div>
      <section v-else-if="detailError" class="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p class="text-sm font-medium text-red-700">{{ detailError }}</p>
        <BaseButton class="mt-3" variant="secondary" @click="loadItems">Thử lại</BaseButton>
      </section>
      <div v-else class="space-y-4">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
              <tr><th class="px-3 py-2">Dịch vụ</th><th class="px-3 py-2 text-right">SL</th><th class="px-3 py-2 text-right">Đơn giá</th><th class="px-3 py-2 text-right">Thành tiền</th></tr>
            </thead>
            <tbody class="divide-y divide-slate-200">
              <tr v-for="item in invoiceItems" :key="item.id">
                <td class="px-3 py-2">{{ item.description }}</td>
                <td class="px-3 py-2 text-right">{{ item.quantity }}</td>
                <td class="px-3 py-2 text-right">{{ formatCurrency(item.unitPrice) }}</td>
                <td class="px-3 py-2 text-right font-semibold">{{ formatCurrency(item.lineTotal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <dl class="space-y-2 text-sm">
          <div class="flex justify-between"><dt>Tạm tính</dt><dd>{{ formatCurrency(selectedInvoice?.subtotal) }}</dd></div>
          <div class="flex justify-between"><dt>Giảm giá</dt><dd>{{ formatCurrency(selectedInvoice?.discountAmount) }}</dd></div>
          <div class="flex justify-between font-semibold text-slate-950"><dt>Tổng tiền</dt><dd>{{ formatCurrency(selectedInvoice?.totalAmount) }}</dd></div>
          <div v-if="selectedInvoice?.paidAt" class="flex justify-between"><dt>Thời điểm thu</dt><dd>{{ formatDateTime(selectedInvoice.paidAt) }}</dd></div>
        </dl>
      </div>
      <template #footer>
        <BaseButton variant="secondary" @click="detailOpen = false">Đóng</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="collectOpen"
      title="Thu tiền hóa đơn"
      :message="collectMessage"
      confirm-text="Đã thu"
      :loading="collecting"
      @confirm="collectInvoice"
    />
  </ReceptionistLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, ConfirmDialog, DataTable, FormModal, LoadingSpinner, PageHeader, Pagination, StatusBadge } from '@/components/ui'
import ReceptionistLayout from '@/layouts/ReceptionistLayout.vue'
import { receptionApi } from '@/api/reception'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams } from '@/utils/params'
import { formatCurrency, formatDateTime } from '@/utils/formatters'
import { invoiceStatus } from '@/utils/statusLabels'

const statusOptions = [
  { label: 'Chưa thu', value: 'UNPAID' },
  { label: 'Đã thu', value: 'PAID' },
  { label: 'Đã hủy', value: 'VOID' }
]
const columns = [
  { key: 'invoiceCode', label: 'Mã hóa đơn' },
  { key: 'patient', label: 'Bệnh nhân' },
  { key: 'issuedAt', label: 'Ngày lập' },
  { key: 'totalAmount', label: 'Tổng tiền' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const filters = reactive({ status: 'UNPAID', from: '', to: '', keyword: '' })
const invoices = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })
const { success, error: toastError } = useToast()

const selectedInvoice = ref(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const invoiceItems = ref([])
const collectOpen = ref(false)
const collecting = ref(false)

const collectMessage = computed(() => selectedInvoice.value
  ? `Xác nhận đã thu ${formatCurrency(selectedInvoice.value.totalAmount)} cho hóa đơn ${selectedInvoice.value.invoiceCode} của ${selectedInvoice.value.patientName}.`
  : '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await receptionApi.invoices.list(cleanParams({ ...filters, page: page.value, size: size.value }))
    invoices.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    invoices.value = []
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

async function loadItems() {
  if (!selectedInvoice.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    invoiceItems.value = await receptionApi.invoices.items(selectedInvoice.value.id)
  } catch (err) {
    invoiceItems.value = []
    detailError.value = apiErrorMessage(err)
  } finally {
    detailLoading.value = false
  }
}

function openDetail(row) {
  selectedInvoice.value = row
  detailOpen.value = true
  loadItems()
}

function openCollect(row) {
  selectedInvoice.value = row
  collectOpen.value = true
}

function replaceOrRemove(updated) {
  if (filters.status === 'UNPAID') {
    invoices.value = invoices.value.filter((item) => item.id !== updated.id)
    return
  }
  const index = invoices.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) invoices.value.splice(index, 1, updated)
}

async function collectInvoice() {
  if (!selectedInvoice.value || collecting.value) return
  collecting.value = true
  try {
    const updated = await receptionApi.invoices.collect(selectedInvoice.value.id)
    replaceOrRemove(updated)
    success(`Đã thu ${formatCurrency(updated.totalAmount)}`)
    collectOpen.value = false
  } catch (err) {
    const message = apiErrorMessage(err)
    toastError(message)
    if (err?.status === 409) await load()
  } finally {
    collecting.value = false
  }
}

onMounted(load)
</script>
