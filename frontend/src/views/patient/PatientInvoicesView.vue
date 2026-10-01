<template>
  <PatientLayout>
    <PageHeader title="Hóa đơn của tôi" eyebrow="Bệnh nhân" description="Danh sách hóa đơn đã lập. Hệ thống chỉ hiển thị trạng thái đã thu, không có thanh toán trực tuyến." />

    <section class="space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-4" @submit.prevent="reload">
        <BaseDatePicker id="patient-invoice-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="patient-invoice-to" v-model="filters.to" label="Đến ngày" />
        <BaseSelect id="patient-invoice-status" v-model="filters.status" label="Trạng thái" :options="statusOptions" />
        <BaseButton type="submit" variant="secondary" class="self-end">Lọc</BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có hóa đơn">
        <template #cell-code="{ row }">
          <span class="font-semibold text-slate-900">{{ row.invoiceCode || row.code || `#${row.id}` }}</span>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.status" :label="invoiceStatus[row.status] || row.status" />
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
import { invoicesApi } from '@/api/invoices'
import { usePagination } from '@/composables/usePagination'
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
  { key: 'issuedAt', label: 'Ngày lập', formatter: formatDate },
  { key: 'totalAmount', label: 'Tổng giá trị', formatter: formatCurrency },
  { key: 'status', label: 'Trạng thái' }
]

const filters = reactive({ from: '', to: '', status: '' })
const rows = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await invoicesApi.mine({ ...filters, page: page.value, size: size.value })
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
