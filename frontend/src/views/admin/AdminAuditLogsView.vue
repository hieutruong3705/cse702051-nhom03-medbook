<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Audit log" description="Tra cứu các sự kiện nhạy cảm trong hệ thống." />

      <section class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_1fr_160px_160px_auto]">
        <BaseInput id="audit-actor" v-model="filters.actorUserId" label="Actor user ID" />
        <BaseInput id="audit-action" v-model="filters.actionCode" label="Mã hành động" />
        <BaseDatePicker id="audit-from" v-model="filters.from" label="Từ ngày" />
        <BaseDatePicker id="audit-to" v-model="filters.to" label="Đến ngày" />
        <div class="flex items-end">
          <BaseButton class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass" @click="loadLogs">Lọc</BaseButton>
        </div>
      </section>

      <DataTable :columns="columns" :rows="logs" :loading="loading" :error="error" empty-text="Chưa có audit log phù hợp">
        <template #cell-createdAt="{ value }">
          {{ formatDateTime(value) }}
        </template>
        <template #cell-actionCode="{ value }">
          <span class="font-mono text-xs font-semibold text-slate-800">{{ value }}</span>
        </template>
      </DataTable>
    </div>
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseDatePicker from '@/components/ui/BaseDatePicker.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import DataTable from '@/components/ui/DataTable.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { auditLogsApi } from '@/api/auditLogs'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'

const filters = reactive({ actorUserId: '', actionCode: '', from: '', to: '' })
const logs = ref([])
const loading = ref(false)
const error = ref('')
const columns = [
  { key: 'createdAt', label: 'Thời gian' },
  { key: 'actorUserId', label: 'Actor' },
  { key: 'actionCode', label: 'Hành động' },
  { key: 'entityType', label: 'Đối tượng' },
  { key: 'entityId', label: 'ID' }
]

const loadLogs = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await auditLogsApi.list({ ...filters })
    logs.value = Array.isArray(response?.content) ? response.content : response || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>
