<template>
  <PatientLayout>
    <PageHeader title="Bệnh án và lịch sử khám" eyebrow="Bệnh nhân" description="Tóm tắt hồ sơ y tế và các lần khám đã ghi nhận." />

    <section class="grid gap-4 lg:grid-cols-[320px_1fr]">
      <aside class="rounded-md border border-slate-200 bg-white p-4">
        <h2 class="font-semibold text-slate-950">Tóm tắt bệnh án</h2>
        <div v-if="recordLoading" class="mt-4"><LoadingSpinner label="Đang tải bệnh án" /></div>
        <p v-else-if="recordError" class="mt-3 text-sm text-red-700">{{ recordError }}</p>
        <dl v-else class="mt-4 space-y-3 text-sm">
          <div><dt class="text-slate-500">Mã hồ sơ</dt><dd class="font-medium">{{ record.recordCode || record.code || '-' }}</dd></div>
          <div><dt class="text-slate-500">Nhóm máu</dt><dd>{{ record.bloodType || record.patient?.bloodType || '-' }}</dd></div>
          <div><dt class="text-slate-500">Dị ứng</dt><dd>{{ record.allergyNotes || record.allergies || '-' }}</dd></div>
          <div><dt class="text-slate-500">Bệnh mạn tính</dt><dd>{{ record.chronicConditions || '-' }}</dd></div>
        </dl>
      </aside>

      <div>
        <DataTable :columns="columns" :rows="encounters" :loading="encounterLoading" :error="encounterError" empty-text="Chưa có lịch sử khám">
          <template #cell-date="{ row }">
            {{ formatDate(row.encounterDate || row.createdAt || row.updatedAt) }}
          </template>
          <template #cell-status="{ row }">
            <StatusBadge :value="row.status" :label="encounterStatus[row.status] || row.status" />
          </template>
        </DataTable>
      </div>
    </section>
  </PatientLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { DataTable, LoadingSpinner, PageHeader, StatusBadge } from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { encountersApi } from '@/api/encounters'
import { recordsApi } from '@/api/records'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate } from '@/utils/formatters'
import { encounterStatus } from '@/utils/statusLabels'

const columns = [
  { key: 'date', label: 'Ngày khám' },
  { key: 'doctorName', label: 'Bác sĩ', formatter: (value, row) => value || row.doctor?.fullName || '-' },
  { key: 'chiefComplaint', label: 'Lý do khám', formatter: (value) => value || '-' },
  { key: 'diagnosis', label: 'Chẩn đoán', formatter: (value) => value || '-' },
  { key: 'status', label: 'Trạng thái' }
]

const record = ref({})
const encounters = ref([])
const recordLoading = ref(false)
const encounterLoading = ref(false)
const recordError = ref('')
const encounterError = ref('')

async function loadRecord() {
  recordLoading.value = true
  recordError.value = ''
  try {
    record.value = await recordsApi.mine()
  } catch (err) {
    recordError.value = apiErrorMessage(err)
  } finally {
    recordLoading.value = false
  }
}

async function loadEncounters() {
  encounterLoading.value = true
  encounterError.value = ''
  try {
    const response = await encountersApi.mine({ page: 0, size: 20 })
    encounters.value = response.content ?? response.items ?? response
  } catch (err) {
    encounterError.value = apiErrorMessage(err)
  } finally {
    encounterLoading.value = false
  }
}

onMounted(() => {
  loadRecord()
  loadEncounters()
})
</script>
