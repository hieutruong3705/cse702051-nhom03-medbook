<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Nhật ký hệ thống" eyebrow="Quản trị" description="Tra cứu các sự kiện nhạy cảm: đăng nhập, khóa tài khoản, đổi vai trò, xem bệnh án, xóa dữ liệu, xuất báo cáo." />

      <form class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[140px_1fr_170px_170px_auto]" @submit.prevent="reload">
        <BaseInput id="audit-actor" v-model="filters.actorUserId" label="Mã người dùng" type="number" placeholder="Ví dụ: 1" />
        <BaseSelect id="audit-action" v-model="filters.actionCode" label="Hành động" placeholder="Tất cả" :options="actionOptions" />
        <BaseDatePicker id="audit-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="audit-to" v-model="filters.to" label="Đến ngày" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass">Lọc</BaseButton>
        </div>
      </form>

      <DataTable :columns="columns" :rows="logs" :loading="loading" :error="error" empty-text="Chưa có sự kiện phù hợp">
        <template #cell-createdAt="{ value }">{{ formatDateTime(value) }}</template>
        <template #cell-actionCode="{ value }">
          <span class="font-mono text-xs font-semibold text-slate-800">{{ value }}</span>
        </template>
        <template #cell-entity="{ row }">{{ row.entityType }}<span v-if="row.entityId"> #{{ row.entityId }}</span></template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </div>
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { BaseButton, BaseDatePicker, BaseInput, BaseSelect, DataTable, PageHeader, Pagination } from '@/components/ui'
import { auditLogsApi } from '@/api/auditLogs'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'
import { cleanParams } from '@/utils/params'

// Chỉ hiển thị dữ liệu hành chính của sự kiện; không hiển thị metadata vì có thể chứa mã bệnh án.
const columns = [
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'actorUserId', label: 'Người thực hiện', formatter: (value) => (value ? `#${value}` : 'Khách') },
  { key: 'actionCode', label: 'Hành động' },
  { key: 'entity', label: 'Đối tượng' },
  { key: 'ipAddress', label: 'Địa chỉ IP', formatter: (value) => value || '-' }
]

const filters = reactive({ actorUserId: '', actionCode: '', from: '', to: '' })
const logs = ref([])
const actionOptions = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })

async function loadLogs() {
  loading.value = true
  error.value = ''
  try {
    const response = await auditLogsApi.list(cleanParams({ ...filters, page: page.value, size: size.value }))
    logs.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    logs.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return loadLogs()
}

function changePage(next) {
  page.value = next
  loadLogs()
}

onMounted(async () => {
  loadLogs()
  try {
    const codes = await auditLogsApi.actionCodes()
    actionOptions.value = (codes ?? []).map((code) => ({ label: code, value: code }))
  } catch {
    actionOptions.value = []
  }
})
</script>
