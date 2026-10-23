<template>
  <AdminLayout>
    <PageHeader
      title="Hóa đơn"
      eyebrow="Quản trị"
      description="Theo dõi hóa đơn, ghi nhận đã thu tại quầy hoặc hủy hóa đơn chưa thu. Hóa đơn đã lập chưa có nghĩa là đã thu tiền."
    />

    <section class="mt-4 space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-5" @submit.prevent="reload">
        <BaseDatePicker id="admin-invoice-from" v-model="filters.from" label="Lập từ ngày" />
        <BaseDatePicker id="admin-invoice-to" v-model="filters.to" label="Đến ngày" />
        <BaseSelect id="admin-invoice-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <BaseInput id="admin-invoice-keyword" v-model="filters.keyword" label="Tìm kiếm" placeholder="Mã hóa đơn, bệnh nhân" />
        <BaseButton type="submit" variant="secondary" class="self-end" icon="fa-solid fa-filter">Lọc</BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có hóa đơn phù hợp">
        <template #cell-code="{ row }">
          <button type="button" class="font-semibold text-primary-700 hover:underline" @click="openDetails(row)">
            {{ row.invoiceCode || `#${row.id}` }}
          </button>
        </template>
        <template #cell-patientName="{ row }">
          <p class="text-slate-900">{{ row.patientName || '-' }}</p>
          <p class="text-xs text-slate-500">{{ row.patientCode }}</p>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="invoiceStatus[row.status] || row.status" />
        </template>
        <template #cell-actions="{ row }">
          <!-- Chỉ hóa đơn chưa thu mới thu hoặc hủy được; máy chủ từ chối các trường hợp khác bằng 409. -->
          <div class="flex flex-wrap gap-2">
            <BaseButton variant="secondary" :disabled="row.status !== 'UNPAID'" :aria-label="`Ghi nhận đã thu ${row.invoiceCode}`" @click="confirmCollect(row)">
              Đã thu
            </BaseButton>
            <BaseButton variant="danger" :disabled="row.status !== 'UNPAID'" :aria-label="`Hủy hóa đơn ${row.invoiceCode}`" @click="openVoid(row)">
              Hủy
            </BaseButton>
          </div>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </section>

    <FormModal v-model="detailsOpen" title="Chi tiết hóa đơn">
      <div v-if="selected" class="space-y-4">
        <dl class="grid gap-3 text-sm md:grid-cols-2">
          <div><dt class="text-slate-500">Mã hóa đơn</dt><dd class="font-semibold">{{ selected.invoiceCode || `#${selected.id}` }}</dd></div>
          <div><dt class="text-slate-500">Trạng thái</dt><dd><StatusBadge :value="selected.status" :label="invoiceStatus[selected.status] || selected.status" /></dd></div>
          <div><dt class="text-slate-500">Tổng giá trị hóa đơn</dt><dd class="font-semibold">{{ formatCurrency(selected.totalAmount) }}</dd></div>
          <div><dt class="text-slate-500">Đã thu</dt><dd class="font-semibold">{{ formatCurrency(selected.status === 'PAID' ? selected.totalAmount : 0) }}</dd></div>
          <div v-if="selected.discountAmount > 0"><dt class="text-slate-500">Giảm giá</dt><dd>{{ formatCurrency(selected.discountAmount) }}</dd></div>
          <div v-if="selected.paidAt"><dt class="text-slate-500">Thời điểm thu</dt><dd>{{ formatDateTime(selected.paidAt) }}</dd></div>
          <div v-if="selected.voidReason" class="md:col-span-2"><dt class="text-slate-500">Lý do hủy</dt><dd>{{ selected.voidReason }}</dd></div>
        </dl>
        <DataTable :columns="itemColumns" :rows="items" :loading="itemsLoading" :error="itemsError" empty-text="Chưa có dòng hóa đơn" />
      </div>
      <template #footer>
        <BaseButton variant="secondary" @click="detailsOpen = false">Đóng</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="collectOpen"
      title="Ghi nhận đã thu?"
      :message="`Ghi nhận đã thu ${formatCurrency(selected?.totalAmount)} cho hóa đơn ${selected?.invoiceCode || ''} tại quầy. Thao tác này không hoàn tác được.`"
      confirm-text="Đã thu"
      :loading="acting"
      @confirm="collect"
    />

    <FormModal v-model="voidOpen" title="Hủy hóa đơn?" :description="`Hóa đơn ${selected?.invoiceCode || ''} sẽ không còn được tính vào doanh thu. Thao tác này không hoàn tác được.`">
      <BaseTextarea id="void-reason" v-model="voidReason" label="Lý do hủy" :rows="3" required :error="voidError" />
      <template #footer>
        <BaseButton variant="secondary" @click="voidOpen = false">Đóng</BaseButton>
        <BaseButton variant="danger" :loading="acting" @click="voidInvoice">Hủy hóa đơn</BaseButton>
      </template>
    </FormModal>
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
  BaseTextarea,
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
import { formatCurrency, formatDate, formatDateTime } from '@/utils/formatters'
import { cleanParams } from '@/utils/params'
import { invoiceStatus } from '@/utils/statusLabels'

const statusOptions = [
  { label: 'Chưa thu', value: 'UNPAID' },
  { label: 'Đã thu', value: 'PAID' },
  { label: 'Đã hủy', value: 'VOID' }
]
const columns = [
  { key: 'code', label: 'Hóa đơn' },
  { key: 'patientName', label: 'Bệnh nhân' },
  { key: 'issuedAt', label: 'Ngày lập', formatter: formatDate },
  { key: 'totalAmount', label: 'Tổng giá trị', formatter: formatCurrency },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]
// Dòng hóa đơn chỉ có mô tả dịch vụ đã chốt lúc lập (description), không tham chiếu tên dịch vụ hiện tại.
const itemColumns = [
  { key: 'description', label: 'Dịch vụ' },
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
const selected = ref(null)
const detailsOpen = ref(false)
const items = ref([])
const itemsLoading = ref(false)
const itemsError = ref('')
const collectOpen = ref(false)
const voidOpen = ref(false)
const voidReason = ref('')
const voidError = ref('')
const acting = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await invoicesApi.listAdmin(cleanParams({ ...filters, page: page.value, size: size.value }))
    rows.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    rows.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return load()
}

function changePage(next) {
  page.value = next
  load()
}

async function openDetails(row) {
  selected.value = row
  items.value = []
  itemsError.value = ''
  itemsLoading.value = true
  detailsOpen.value = true
  try {
    items.value = (await invoicesApi.items(row.id)) ?? []
  } catch (err) {
    itemsError.value = apiErrorMessage(err)
  } finally {
    itemsLoading.value = false
  }
}

function confirmCollect(row) {
  selected.value = row
  collectOpen.value = true
}

async function collect() {
  if (!selected.value || acting.value) return
  acting.value = true
  try {
    await invoicesApi.collect(selected.value.id, {})
    success('Đã ghi nhận đã thu')
    collectOpen.value = false
  } catch (err) {
    // 409: hóa đơn vừa được người khác thu hoặc hủy. Báo đúng thông điệp của máy chủ rồi tải lại.
    toastError(apiErrorMessage(err))
    collectOpen.value = false
  } finally {
    acting.value = false
    await load()
  }
}

function openVoid(row) {
  selected.value = row
  voidReason.value = ''
  voidError.value = ''
  voidOpen.value = true
}

async function voidInvoice() {
  if (!selected.value || acting.value) return
  const reason = voidReason.value.trim()
  if (!reason) {
    voidError.value = 'Phải nhập lý do hủy'
    return
  }
  acting.value = true
  voidError.value = ''
  try {
    await invoicesApi.void(selected.value.id, { reason })
    success('Đã hủy hóa đơn')
    voidOpen.value = false
    await load()
  } catch (err) {
    if (err?.status === 409) {
      toastError(apiErrorMessage(err))
      voidOpen.value = false
      await load()
    } else {
      voidError.value = err?.details?.reason || apiErrorMessage(err)
    }
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>
