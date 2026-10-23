<template>
  <DoctorLayout>
    <PageHeader
      eyebrow="Khám bệnh"
      :title="encounter ? `Lần khám #${encounter.id}` : 'Lần khám'"
      :description="encounter ? `Bệnh nhân: ${encounter.patientName || '—'}` : ''"
    >
      <template #actions>
        <RouterLink to="/doctor/appointments" class="text-sm font-semibold text-primary-700 hover:text-primary-900">
          ← Lịch khám
        </RouterLink>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="loading" class="mt-6" label="Đang tải lần khám" />

    <div v-else-if="loadError" class="mt-6 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert" data-test="load-error">
      <p class="font-semibold">{{ loadError }}</p>
      <BaseButton v-if="canRetry" class="mt-3" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>

    <template v-else-if="encounter">
      <section class="mt-6 rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-label="Thông tin lần khám" data-test="encounter-summary">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <dl class="grid flex-1 gap-4 text-sm sm:grid-cols-3">
            <div><dt class="text-slate-500">Bệnh nhân</dt><dd class="font-semibold text-slate-950">{{ encounter.patientName || '—' }}</dd></div>
            <div><dt class="text-slate-500">Bắt đầu lúc</dt><dd class="font-semibold text-slate-950">{{ formatDateTime(encounter.encounterAt) }}</dd></div>
            <div><dt class="text-slate-500">Lịch hẹn</dt><dd class="font-semibold text-slate-950">{{ encounter.appointmentId ? `#${encounter.appointmentId}` : '—' }}</dd></div>
          </dl>
          <StatusBadge :value="encounter.status" :label="encounterStatus[encounter.status] || encounter.status" />
        </div>
      </section>

      <div class="mt-6 flex gap-1 overflow-x-auto border-b border-slate-200" role="tablist" aria-label="Các phần của lần khám" @keydown.right.prevent="move(1)" @keydown.left.prevent="move(-1)">
        <button
          v-for="tab in tabs"
          :id="`tab-${tab.id}`"
          :key="tab.id"
          type="button"
          role="tab"
          class="whitespace-nowrap border-b-2 px-4 py-2 text-sm font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-600"
          :class="active === tab.id ? 'border-primary-600 text-primary-700' : 'border-transparent text-slate-600 hover:text-slate-900'"
          :aria-selected="active === tab.id"
          :aria-controls="`panel-${tab.id}`"
          :tabindex="active === tab.id ? 0 : -1"
          :data-test="`tab-${tab.id}`"
          @click="select(tab.id)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="mt-6">
        <div
          v-for="tab in tabs"
          v-show="active === tab.id"
          :id="`panel-${tab.id}`"
          :key="tab.id"
          role="tabpanel"
          :aria-labelledby="`tab-${tab.id}`"
          tabindex="0"
        >
          <!-- Mỗi tab chỉ được dựng khi mở lần đầu, sau đó giữ nguyên để không mất nội dung đang nhập. -->
          <template v-if="visited.has(tab.id)">
            <EncounterExamTab
              v-if="tab.id === 'exam'"
              :encounter="encounter"
              :autosave-ms="autosaveMs"
              @saved="onSaved"
              @dirty-change="(value) => (dirty = value)"
              @stale="refresh"
            />
            <EncounterPrescriptionsTab v-else-if="tab.id === 'prescriptions'" :encounter-id="encounter.id" :editable="encounter.editable" @stale="refresh" />
            <EncounterAttachmentsTab v-else-if="tab.id === 'attachments'" :encounter-id="encounter.id" :editable="encounter.editable" @stale="refresh" />
            <EncounterInvoiceTab v-else :encounter-id="encounter.id" />
          </template>
        </div>
      </div>
    </template>
  </DoctorLayout>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { BaseButton, LoadingSpinner, PageHeader, StatusBadge } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { encountersApi } from '@/api/encounters'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'
import { encounterStatus } from '@/utils/statusLabels'
import EncounterAttachmentsTab from './EncounterAttachmentsTab.vue'
import EncounterExamTab from './EncounterExamTab.vue'
import EncounterInvoiceTab from './EncounterInvoiceTab.vue'
import EncounterPrescriptionsTab from './EncounterPrescriptionsTab.vue'

defineProps({
  autosaveMs: { type: Number, default: 2500 }
})

const LEAVE_MESSAGE = 'Bạn có thay đổi chưa lưu trong lần khám này. Rời trang sẽ mất các thay đổi đó. Vẫn rời đi?'

const route = useRoute()

const tabs = [
  { id: 'exam', label: 'Khám' },
  { id: 'prescriptions', label: 'Đơn thuốc' },
  { id: 'attachments', label: 'Tệp đính kèm' },
  { id: 'invoice', label: 'Hóa đơn' }
]

const encounter = ref(null)
const loading = ref(false)
const loadError = ref('')
const canRetry = ref(true)
const active = ref('exam')
const visited = reactive(new Set(['exam']))
const dirty = ref(false)

const encounterId = computed(() => route.params.id)

async function load() {
  loading.value = true
  loadError.value = ''
  canRetry.value = true
  try {
    encounter.value = await encountersApi.get(encounterId.value)
  } catch (err) {
    encounter.value = null
    if (err?.status === 403) {
      loadError.value = 'Bạn không phụ trách lần khám này nên không thể xem hoặc chỉnh sửa.'
      canRetry.value = false
    } else if (err?.status === 404) {
      loadError.value = 'Không tìm thấy lần khám.'
      canRetry.value = false
    } else {
      loadError.value = apiErrorMessage(err)
    }
  } finally {
    loading.value = false
  }
}

/** Tải lại lần khám ở nền (không hiện spinner) khi server báo trạng thái đã đổi. */
async function refresh() {
  try {
    encounter.value = await encountersApi.get(encounterId.value)
  } catch {
    // giữ dữ liệu đang hiển thị; thông báo lỗi chính đã có ở tab gọi
  }
}

function onSaved(updated) {
  encounter.value = updated
}

function select(id) {
  active.value = id
  visited.add(id)
}

function move(step) {
  const index = tabs.findIndex((tab) => tab.id === active.value)
  const next = tabs[(index + step + tabs.length) % tabs.length]
  select(next.id)
  document.getElementById(`tab-${next.id}`)?.focus()
}

function warnBeforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(() => {
  if (dirty.value && !window.confirm(LEAVE_MESSAGE)) return false
  return true
})

onMounted(() => {
  window.addEventListener('beforeunload', warnBeforeUnload)
  load()
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnBeforeUnload))
</script>
