<template>
  <PatientLayout>
    <PageHeader eyebrow="Bệnh án" :title="encounter ? `Lần khám ngày ${formatDate(encounter.encounterAt)}` : 'Chi tiết lần khám'">
      <template #actions>
        <RouterLink to="/patient/records" class="text-sm font-semibold text-primary-700 hover:text-primary-900">
          ← Bệnh án của tôi
        </RouterLink>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="loading" class="mt-6" label="Đang tải lần khám" />

    <div v-else-if="loadError" class="mt-6 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert" data-test="load-error">
      <p class="font-semibold">{{ loadError }}</p>
      <BaseButton v-if="canRetry" class="mt-3" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>

    <div v-else-if="encounter" class="mt-6 space-y-6">
      <section class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="enc-info">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 id="enc-info" class="text-lg font-semibold text-slate-950">Kết quả khám</h2>
          <StatusBadge :value="encounter.status" :label="encounterStatus[encounter.status] || encounter.status" />
        </div>
        <p v-if="encounter.status !== 'COMPLETED'" class="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="status" data-test="open-note">
          Lần khám chưa hoàn thành nên nội dung dưới đây có thể còn thay đổi.
        </p>
        <dl class="mt-4 grid gap-4 text-sm sm:grid-cols-2">
          <div><dt class="text-slate-500">Bác sĩ</dt><dd class="font-semibold text-slate-950">{{ encounter.doctorName || '—' }}</dd></div>
          <div><dt class="text-slate-500">Thời gian khám</dt><dd class="font-semibold text-slate-950">{{ formatDateTime(encounter.encounterAt) }}</dd></div>
          <div v-for="field in fields" :key="field.key" class="sm:col-span-2" :data-test="`field-${field.key}`">
            <dt class="text-slate-500">{{ field.label }}</dt>
            <dd class="whitespace-pre-line text-slate-900">{{ encounter[field.key] || '—' }}</dd>
          </div>
        </dl>
      </section>

      <section class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="enc-rx" data-test="prescriptions">
        <h2 id="enc-rx" class="text-lg font-semibold text-slate-950">Đơn thuốc</h2>
        <p v-if="rxError" class="mt-3 text-sm text-red-700" role="alert">{{ rxError }}</p>
        <p v-else-if="!prescriptions.length" class="mt-3 text-sm text-slate-500" data-test="rx-empty">Lần khám này không có đơn thuốc.</p>
        <div v-for="rx in prescriptions" :key="rx.id" class="mt-4">
          <p class="text-sm font-semibold text-slate-900">Đơn {{ rx.prescriptionCode || `#${rx.id}` }} <span class="font-normal text-slate-500">· {{ formatDateTime(rx.issuedAt) }}</span></p>
          <p v-if="rx.notes" class="mt-1 whitespace-pre-line text-sm text-slate-700">{{ rx.notes }}</p>
          <ul class="mt-2 divide-y divide-slate-100 rounded-md border border-slate-200 text-sm">
            <li v-for="item in rx.items" :key="item.id" class="px-3 py-2" :data-test="`item-${item.id}`">
              <p class="font-medium text-slate-900">{{ item.medicineName }} <span class="font-normal text-slate-500">× {{ item.quantity }}</span></p>
              <p class="text-slate-600">{{ itemDetail(item) }}</p>
            </li>
            <li v-if="!rx.items.length" class="px-3 py-2 text-slate-500">Đơn chưa có thuốc nào.</li>
          </ul>
        </div>
      </section>

      <section class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="enc-att" data-test="attachments">
        <h2 id="enc-att" class="text-lg font-semibold text-slate-950">Tệp đính kèm</h2>
        <p v-if="attError" class="mt-3 text-sm text-red-700" role="alert">{{ attError }}</p>
        <p v-else-if="!attachments.length" class="mt-3 text-sm text-slate-500" data-test="att-empty">Không có tệp đính kèm.</p>
        <ul v-else class="mt-3 divide-y divide-slate-100 rounded-md border border-slate-200 text-sm">
          <li v-for="file in attachments" :key="file.id" class="flex flex-wrap items-center justify-between gap-2 px-3 py-2" :data-test="`attachment-${file.id}`">
            <span class="truncate font-medium text-slate-900">{{ file.originalFileName }}</span>
            <BaseButton size="sm" variant="secondary" icon="fa-solid fa-download" :loading="downloadingId === file.id" @click="download(file)">Tải xuống</BaseButton>
          </li>
        </ul>
        <p v-if="downloadError" class="mt-3 text-sm text-red-700" role="alert" data-test="download-error">{{ downloadError }}</p>
      </section>

      <section class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="enc-inv" data-test="invoice">
        <h2 id="enc-inv" class="text-lg font-semibold text-slate-950">Hóa đơn</h2>
        <p v-if="invError" class="mt-3 text-sm text-red-700" role="alert">{{ invError }}</p>
        <p v-else-if="!invoice" class="mt-3 text-sm text-slate-500" data-test="inv-empty">Lần khám này chưa có hóa đơn.</p>
        <div v-else class="mt-3 text-sm">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="font-semibold text-slate-900">{{ invoice.invoiceCode }}</p>
            <StatusBadge :value="invoice.status" :label="invoiceStatus[invoice.status] || invoice.status" />
          </div>
          <p class="mt-2 text-base font-semibold text-slate-950">Tổng cộng: {{ formatCurrency(invoice.totalAmount) }}</p>
          <RouterLink to="/patient/invoices" class="mt-2 inline-block font-semibold text-primary-700 hover:text-primary-900">Xem tất cả hóa đơn →</RouterLink>
        </div>
      </section>
    </div>
  </PatientLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BaseButton, LoadingSpinner, PageHeader, StatusBadge } from '@/components/ui'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { attachmentsApi } from '@/api/attachments'
import { encountersApi } from '@/api/encounters'
import { invoicesApi } from '@/api/invoices'
import { prescriptionItemsApi } from '@/api/prescriptionItems'
import { prescriptionsApi } from '@/api/prescriptions'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatDate, formatDateTime } from '@/utils/formatters'
import { encounterStatus, invoiceStatus } from '@/utils/statusLabels'
import { saveBlob } from '@/views/doctor/encounter/encounterUtils'

const fields = [
  { key: 'chiefComplaint', label: 'Lý do khám' },
  { key: 'diagnosis', label: 'Chẩn đoán' },
  { key: 'clinicalNotes', label: 'Ghi chú lâm sàng' },
  { key: 'treatmentPlan', label: 'Kế hoạch điều trị' },
  { key: 'followUpNote', label: 'Dặn dò / tái khám' }
]

const route = useRoute()

const encounter = ref(null)
const loading = ref(false)
const loadError = ref('')
const canRetry = ref(true)

const prescriptions = ref([])
const rxError = ref('')
const attachments = ref([])
const attError = ref('')
const invoice = ref(null)
const invError = ref('')
const downloadingId = ref(null)
const downloadError = ref('')

const list = (response) => (Array.isArray(response) ? response : (response?.content ?? []))

const itemDetail = (item) =>
  [item.dosage, item.frequency, item.durationDays ? `${item.durationDays} ngày` : '', item.instructions].filter(Boolean).join(' · ') ||
  'Chưa có hướng dẫn chi tiết.'

async function loadPrescriptions(id) {
  rxError.value = ''
  try {
    const rows = list(await prescriptionsApi.listByEncounter(id))
    prescriptions.value = await Promise.all(
      rows.map(async (rx) => ({ ...rx, items: list(await prescriptionItemsApi.listByPrescription(rx.id)) }))
    )
  } catch (err) {
    rxError.value = apiErrorMessage(err)
  }
}

async function loadAttachments(id) {
  attError.value = ''
  try {
    attachments.value = list(await attachmentsApi.listByEncounter(id))
  } catch (err) {
    attError.value = apiErrorMessage(err)
  }
}

async function loadInvoice(id) {
  invError.value = ''
  invoice.value = null
  try {
    invoice.value = await invoicesApi.byEncounter(id)
  } catch (err) {
    if (err?.status !== 404) invError.value = apiErrorMessage(err) // 404 = chưa lập hóa đơn
  }
}

async function load() {
  loading.value = true
  loadError.value = ''
  canRetry.value = true
  try {
    encounter.value = await encountersApi.get(route.params.encounterId)
  } catch (err) {
    encounter.value = null
    if (err?.status === 403 || err?.status === 404) {
      // Không phân biệt "của người khác" với "không tồn tại" để không lộ sự tồn tại của hồ sơ.
      loadError.value = 'Không tìm thấy lần khám này trong bệnh án của bạn.'
      canRetry.value = false
    } else {
      loadError.value = apiErrorMessage(err)
    }
    loading.value = false
    return
  }
  loading.value = false
  const id = encounter.value.id
  await Promise.all([loadPrescriptions(id), loadAttachments(id), loadInvoice(id)])
}

async function download(file) {
  if (downloadingId.value) return
  downloadingId.value = file.id
  downloadError.value = ''
  try {
    saveBlob(await attachmentsApi.download(file.id), file.originalFileName)
  } catch (err) {
    downloadError.value = apiErrorMessage(err)
  } finally {
    downloadingId.value = null
  }
}

onMounted(load)
</script>
