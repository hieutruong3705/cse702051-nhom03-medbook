<template>
  <DoctorLayout>
    <PageHeader :title="patient?.fullName || 'Bệnh nhân'" eyebrow="Bác sĩ" :description="patient ? `Mã bệnh nhân ${patient.patientCode}` : ''">
      <template #actions>
        <RouterLink class="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" to="/doctor/patients">
          <i class="fa-solid fa-arrow-left mr-1" aria-hidden="true"></i>Danh sách bệnh nhân
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="loading" class="mt-6 rounded-md border border-slate-200 bg-white p-6">
      <LoadingSpinner label="Đang tải hồ sơ bệnh nhân" />
    </div>

    <!-- 403: không hiển thị bất kỳ dữ liệu nào của bệnh nhân ngoài phạm vi phụ trách -->
    <div v-else-if="forbidden" class="mt-6">
      <EmptyState
        icon="fa-solid fa-lock"
        title="Bạn không phụ trách bệnh nhân này"
        message="Bác sĩ chỉ xem được hồ sơ và bệnh án của bệnh nhân đã có lịch hẹn hoặc lần khám với mình."
      >
        <RouterLink class="font-semibold text-primary-700 hover:underline" to="/doctor/patients">Về danh sách bệnh nhân</RouterLink>
      </EmptyState>
    </div>

    <div v-else-if="error" class="mt-6 rounded-md border border-slate-200 bg-white p-6 text-center" role="alert">
      <p class="text-sm text-red-700">{{ error }}</p>
      <BaseButton class="mt-4" variant="secondary" icon="fa-solid fa-rotate-right" @click="load">Thử lại</BaseButton>
    </div>

    <div v-else class="mt-4 grid gap-4 xl:grid-cols-2">
      <section class="rounded-md border border-slate-200 bg-white p-5" aria-labelledby="patient-profile-title">
        <h2 id="patient-profile-title" class="font-semibold text-slate-950">Hồ sơ</h2>
        <dl class="mt-3 grid gap-3 text-sm sm:grid-cols-2">
          <div v-for="item in profileRows" :key="item.label">
            <dt class="text-slate-500">{{ item.label }}</dt>
            <dd class="font-medium text-slate-900">{{ item.value || '-' }}</dd>
          </div>
        </dl>
      </section>

      <section class="rounded-md border border-slate-200 bg-white p-5" aria-labelledby="patient-record-title">
        <h2 id="patient-record-title" class="font-semibold text-slate-950">Bệnh án</h2>
        <p v-if="!record" class="mt-3 text-sm text-slate-500">Bệnh nhân chưa có bệnh án.</p>
        <dl v-else class="mt-3 grid gap-3 text-sm">
          <div v-for="item in recordRows" :key="item.label">
            <dt class="text-slate-500">{{ item.label }}</dt>
            <dd class="whitespace-pre-line font-medium text-slate-900">{{ item.value || '-' }}</dd>
          </div>
        </dl>
      </section>

      <section class="xl:col-span-2" aria-labelledby="patient-encounters-title">
        <h2 id="patient-encounters-title" class="mb-2 font-semibold text-slate-950">Lịch sử các lần khám với tôi</h2>
        <DataTable :columns="encounterColumns" :rows="encounters" :error="encountersError" empty-text="Chưa có lần khám nào">
          <template #cell-status="{ value }">
            <StatusBadge :value="value === 'OPEN' ? 'IN_PROGRESS' : value" :label="encounterStatus[value] || value" />
          </template>
          <template #cell-actions="{ row }">
            <RouterLink class="font-semibold text-primary-700 hover:underline" :to="`/doctor/encounters/${row.id}`">
              {{ row.status === 'OPEN' ? 'Tiếp tục khám' : 'Xem lần khám' }}
            </RouterLink>
          </template>
        </DataTable>
      </section>
    </div>
  </DoctorLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BaseButton, DataTable, EmptyState, LoadingSpinner, PageHeader, StatusBadge } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { encountersApi } from '@/api/encounters'
import { patientsApi } from '@/api/patients'
import { recordsApi } from '@/api/records'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatDateTime } from '@/utils/formatters'
import { encounterStatus } from '@/utils/statusLabels'

const genderLabels = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' }
const encounterColumns = [
  { key: 'encounterAt', label: 'Thời điểm khám', formatter: formatDateTime },
  { key: 'chiefComplaint', label: 'Lý do khám', formatter: (value) => value || '-' },
  { key: 'diagnosis', label: 'Chẩn đoán', formatter: (value) => value || '-' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: '' }
]

const route = useRoute()
const patient = ref(null)
const record = ref(null)
const encounters = ref([])
const loading = ref(true)
const forbidden = ref(false)
const error = ref('')
const encountersError = ref('')

const profileRows = computed(() => [
  { label: 'Ngày sinh', value: formatDate(patient.value?.dateOfBirth) },
  { label: 'Giới tính', value: genderLabels[patient.value?.genderCode] || patient.value?.genderCode },
  { label: 'Điện thoại', value: patient.value?.phone },
  { label: 'Email', value: patient.value?.email },
  { label: 'Địa chỉ', value: patient.value?.address },
  { label: 'Nhóm máu', value: patient.value?.bloodType },
  { label: 'Dị ứng', value: patient.value?.allergies },
  { label: 'Liên hệ khẩn cấp', value: [patient.value?.emergencyContactName, patient.value?.emergencyContactPhone].filter(Boolean).join(' · ') }
])

const recordRows = computed(() => [
  { label: 'Mã bệnh án', value: record.value?.recordCode },
  { label: 'Bệnh mạn tính', value: record.value?.chronicConditions },
  { label: 'Ghi chú dị ứng', value: record.value?.allergyNotes },
  { label: 'Tiền sử bệnh', value: record.value?.medicalHistory },
  { label: 'Thuốc đang dùng', value: record.value?.currentMedications }
])

async function load() {
  loading.value = true
  forbidden.value = false
  error.value = ''
  encountersError.value = ''
  patient.value = null
  record.value = null
  encounters.value = []
  const id = route.params.id
  try {
    patient.value = await patientsApi.get(id)
    record.value = await recordsApi.byPatient(id)
  } catch (err) {
    patient.value = null
    record.value = null
    if (err?.status === 403) forbidden.value = true
    else error.value = err?.status === 404 ? 'Không tìm thấy bệnh nhân.' : apiErrorMessage(err)
    loading.value = false
    return
  }
  loading.value = false
  if (record.value?.id) {
    try {
      const response = await encountersApi.list({ medicalRecordId: record.value.id, size: 50 })
      encounters.value = response?.content ?? []
    } catch (err) {
      encountersError.value = apiErrorMessage(err)
    }
  }
}

onMounted(load)
</script>
