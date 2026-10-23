<template>
  <section class="space-y-4" aria-labelledby="att-title">
    <h2 id="att-title" class="text-lg font-semibold text-slate-950">Tệp đính kèm</h2>

    <form v-if="editable" class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" data-test="upload-form" @submit.prevent="upload">
      <FileUpload :key="uploadKey" id="encounter-file" label="Kết quả xét nghiệm / hình ảnh" :error="uploadError" @update:file="onFile" />
      <div class="mt-4 flex justify-end">
        <BaseButton type="submit" icon="fa-solid fa-upload" :loading="uploading" :disabled="!file">Tải lên</BaseButton>
      </div>
    </form>

    <LoadingSpinner v-if="loading" label="Đang tải danh sách tệp" />

    <div v-else-if="loadError" class="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert" data-test="att-load-error">
      <p class="font-semibold">{{ loadError }}</p>
      <BaseButton class="mt-3" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>

    <p v-else-if="!attachments.length" class="rounded-md border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-500" data-test="att-empty">
      Chưa có tệp nào được đính kèm.
    </p>

    <ul v-else class="divide-y divide-slate-200 rounded-md border border-slate-200 bg-white" data-test="att-list">
      <li v-for="item in attachments" :key="item.id" class="flex flex-wrap items-center justify-between gap-3 px-4 py-3" :data-test="`attachment-${item.id}`">
        <div class="min-w-0">
          <p class="truncate font-medium text-slate-900">{{ item.originalFileName }}</p>
          <p class="text-xs text-slate-500">{{ formatFileSize(item.fileSize) }} · tải lên {{ formatDateTime(item.uploadedAt) }}</p>
        </div>
        <div class="flex gap-2">
          <BaseButton size="sm" variant="secondary" icon="fa-solid fa-download" :loading="downloadingId === item.id" @click="download(item)">Tải xuống</BaseButton>
          <BaseButton v-if="editable" size="sm" variant="danger" :aria-label="`Xóa ${item.originalFileName}`" @click="askRemove(item)">Xóa</BaseButton>
        </div>
      </li>
    </ul>

    <p v-if="actionError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="att-action-error">{{ actionError }}</p>

    <ConfirmDialog
      v-model="removeOpen"
      title="Xóa tệp đính kèm?"
      :message="removing ? `Tệp “${removing.originalFileName}” sẽ bị xóa vĩnh viễn.` : ''"
      confirm-text="Xóa tệp"
      danger
      :loading="removeBusy"
      @confirm="confirmRemove"
    />
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { BaseButton, ConfirmDialog, FileUpload, LoadingSpinner } from '@/components/ui'
import { attachmentsApi } from '@/api/attachments'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'
import { formatFileSize, saveBlob } from './encounterUtils'

const props = defineProps({
  encounterId: { type: [Number, String], required: true },
  /** Tải lên/xóa chỉ khi bác sĩ phụ trách và lần khám còn OPEN. */
  editable: { type: Boolean, default: false }
})

const emit = defineEmits(['stale'])
const toast = useToast()

const attachments = ref([])
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')

const file = ref(null)
const uploadKey = ref(0)
const uploading = ref(false)
const uploadError = ref('')
const downloadingId = ref(null)

const removeOpen = ref(false)
const removing = ref(null)
const removeBusy = ref(false)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await attachmentsApi.listByEncounter(props.encounterId)
    attachments.value = Array.isArray(response) ? response : (response?.content ?? [])
  } catch (err) {
    loadError.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function onFile(selected) {
  file.value = selected
  uploadError.value = ''
}

/** Thông điệp riêng cho lỗi tệp của server (413 quá lớn, 415 sai loại), còn lại dùng thông điệp chung. */
function uploadMessage(err) {
  if (err?.status === 413) return 'Tệp vượt quá giới hạn 10 MB.'
  if (err?.status === 415) return 'Chỉ hỗ trợ tệp PDF, JPG hoặc PNG.'
  return apiErrorMessage(err)
}

async function upload() {
  if (!file.value || uploading.value) return
  uploading.value = true
  uploadError.value = ''
  actionError.value = ''
  try {
    const created = await attachmentsApi.upload(props.encounterId, file.value)
    attachments.value = [...attachments.value, created]
    file.value = null
    uploadKey.value += 1 // đặt lại ô chọn tệp
    toast.success('Đã tải tệp lên')
  } catch (err) {
    uploadError.value = uploadMessage(err)
    if (err?.status === 409) emit('stale')
  } finally {
    uploading.value = false
  }
}

async function download(item) {
  if (downloadingId.value) return
  downloadingId.value = item.id
  actionError.value = ''
  try {
    saveBlob(await attachmentsApi.download(item.id), item.originalFileName)
  } catch (err) {
    actionError.value = apiErrorMessage(err)
  } finally {
    downloadingId.value = null
  }
}

function askRemove(item) {
  actionError.value = ''
  removing.value = item
  removeOpen.value = true
}

async function confirmRemove() {
  if (!removing.value || removeBusy.value) return
  const item = removing.value
  removeBusy.value = true
  try {
    await attachmentsApi.remove(item.id)
    attachments.value = attachments.value.filter((existing) => existing.id !== item.id)
    toast.success('Đã xóa tệp')
  } catch (err) {
    actionError.value = apiErrorMessage(err)
    if (err?.status === 409) emit('stale')
  } finally {
    removeBusy.value = false
    removeOpen.value = false
    removing.value = null
  }
}

onMounted(load)
</script>
