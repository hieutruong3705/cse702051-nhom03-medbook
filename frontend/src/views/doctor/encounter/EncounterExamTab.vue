<template>
  <form class="space-y-6" novalidate data-test="exam-form" @submit.prevent="save()">
    <p
      v-if="!editable"
      class="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700"
      role="status"
      data-test="readonly-banner"
    >
      <i class="fa-solid fa-lock mr-2 text-slate-500" aria-hidden="true"></i>
      {{ encounter.status === 'COMPLETED' ? 'Lần khám đã hoàn thành nên nội dung được khóa, chỉ xem.' : 'Bạn không có quyền chỉnh sửa lần khám này.' }}
    </p>

    <section class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="exam-content">
      <h2 id="exam-content" class="text-lg font-semibold text-slate-950">Nội dung khám</h2>
      <div class="mt-4 grid gap-4">
        <BaseTextarea
          v-for="field in ENCOUNTER_FIELDS"
          :id="`encounter-${field.key}`"
          :key="field.key"
          v-model="form[field.key]"
          :label="field.label"
          :rows="field.rows"
          :required="field.key === 'diagnosis'"
          :disabled="locked"
          :error="fieldError(field.key)"
          :hint="field.key === 'diagnosis' && editable ? 'Bắt buộc để hoàn thành khám.' : ''"
        />
      </div>
    </section>

    <section class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="exam-summary">
      <h2 id="exam-summary" class="text-lg font-semibold text-slate-950">Tóm tắt bệnh án</h2>
      <p class="mt-1 text-sm text-slate-600">Các thông tin này được cập nhật vào bệnh án của bệnh nhân.</p>
      <div class="mt-4 grid gap-4 sm:grid-cols-2">
        <BaseSelect
          id="summary-bloodType"
          v-model="form.bloodType"
          label="Nhóm máu"
          :options="BLOOD_TYPE_OPTIONS"
          :disabled="locked"
          :error="fieldError('bloodType')"
        />
        <div class="hidden sm:block"></div>
        <BaseTextarea
          v-for="field in SUMMARY_FIELDS"
          :id="`summary-${field.key}`"
          :key="field.key"
          v-model="form[field.key]"
          :label="field.label"
          :rows="field.rows"
          :disabled="locked"
          :error="fieldError(field.key)"
        />
      </div>
      <p v-if="recordError" class="mt-3 text-sm text-amber-700" role="status" data-test="record-warning">{{ recordError }}</p>
    </section>

    <p v-if="formError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="form-error">{{ formError }}</p>

    <div v-if="editable" class="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <p class="text-sm text-slate-600" role="status" data-test="save-status">{{ saveStatusText }}</p>
      <div class="flex flex-wrap gap-2">
        <BaseButton type="submit" variant="secondary" :loading="saving" :disabled="!dirty || completing" data-test="save-button">Lưu nháp</BaseButton>
        <BaseButton type="button" :disabled="saving || completing" data-test="complete-button" @click="askComplete">Hoàn thành khám</BaseButton>
      </div>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      title="Hoàn thành khám?"
      message="Sau khi hoàn thành, nội dung lần khám và đơn thuốc sẽ bị khóa, lịch hẹn chuyển sang Hoàn thành và bệnh nhân xem được kết quả. Thao tác này không thể hoàn tác."
      confirm-text="Hoàn thành khám"
      :loading="completing"
      @confirm="complete"
    />
  </form>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { BaseButton, BaseSelect, BaseTextarea, ConfirmDialog } from '@/components/ui'
import { encountersApi } from '@/api/encounters'
import { recordsApi } from '@/api/records'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatTime } from '@/utils/formatters'
import {
  BLOOD_TYPE_OPTIONS,
  ENCOUNTER_FIELDS,
  SUMMARY_FIELDS,
  buildChanges,
  serverFieldErrors,
  toFormValues,
  validateLengths
} from './encounterUtils'

const props = defineProps({
  encounter: { type: Object, required: true },
  /** Lưu nháp tự động sau chừng này ms không gõ nữa; 0 để tắt. */
  autosaveMs: { type: Number, default: 2500 }
})

const emit = defineEmits(['saved', 'dirty-change', 'stale'])

const toast = useToast()

const editable = computed(() => props.encounter.editable === true)
const form = reactive(toFormValues(props.encounter, null))
const baseline = ref({ ...form })

const saving = ref(false)
const completing = ref(false)
const confirmOpen = ref(false)
const formError = ref('')
const recordError = ref('')
const serverErrors = ref({})
const lastSavedAt = ref(null)
const saveFailed = ref(false)
let timer = null

const locked = computed(() => !editable.value || saving.value || completing.value)
const changes = computed(() => buildChanges(form, baseline.value))
const dirty = computed(() => Object.keys(changes.value).length > 0)
const lengthErrors = computed(() => validateLengths(form))
const invalid = computed(() => Object.keys(lengthErrors.value).length > 0)

const fieldError = (key) => lengthErrors.value[key] || serverErrors.value[key] || ''

const saveStatusText = computed(() => {
  if (saving.value) return 'Đang lưu…'
  if (saveFailed.value) return 'Lưu không thành công — các thay đổi vẫn còn trên màn hình.'
  if (dirty.value) return props.autosaveMs ? 'Có thay đổi chưa lưu — sẽ tự động lưu nháp.' : 'Có thay đổi chưa lưu.'
  if (lastSavedAt.value) return `Đã lưu lúc ${formatTime(lastSavedAt.value)}`
  return 'Chưa có thay đổi.'
})

function clearTimer() {
  if (timer) {
    window.clearTimeout(timer)
    timer = null
  }
}

function scheduleAutosave() {
  clearTimer()
  if (!props.autosaveMs || !editable.value || !dirty.value || invalid.value) return
  timer = window.setTimeout(() => {
    timer = null
    save({ auto: true })
  }, props.autosaveMs)
}

function handleError(err) {
  saveFailed.value = true
  if (err?.status === 400 && err.details && Object.keys(err.details).length) {
    serverErrors.value = serverFieldErrors(err.details)
    formError.value = 'Vui lòng kiểm tra lại các trường được đánh dấu.'
    return
  }
  formError.value = apiErrorMessage(err)
  // 409: lần khám vừa được hoàn thành ở nơi khác → báo trang tải lại để chuyển sang chế độ chỉ đọc
  if (err?.status === 409) emit('stale')
}

/** Lưu nháp các trường đã đổi. Trả true nếu không còn gì để lưu hoặc lưu thành công. */
async function save() {
  if (!editable.value || saving.value || completing.value) return false
  if (!dirty.value) return true
  if (invalid.value) {
    formError.value = 'Có trường vượt quá độ dài cho phép.'
    return false
  }

  clearTimer()
  saving.value = true
  formError.value = ''
  serverErrors.value = {}
  saveFailed.value = false
  const sent = { ...form }
  try {
    const updated = await encountersApi.update(props.encounter.id, changes.value)
    baseline.value = sent // gõ thêm trong lúc chờ vẫn được tính là chưa lưu
    lastSavedAt.value = new Date()
    emit('saved', updated)
    saving.value = false
    scheduleAutosave() // còn thay đổi gõ trong lúc chờ thì lưu tiếp; lỗi thì KHÔNG tự thử lại liên tục
    return true
  } catch (err) {
    handleError(err)
    return false
  } finally {
    saving.value = false
  }
}

function askComplete() {
  formError.value = ''
  serverErrors.value = {}
  if (!form.diagnosis.trim()) {
    serverErrors.value = { diagnosis: 'Phải nhập chẩn đoán trước khi hoàn thành khám' }
    formError.value = 'Chưa thể hoàn thành khám: thiếu chẩn đoán.'
    return
  }
  if (invalid.value) {
    formError.value = 'Có trường vượt quá độ dài cho phép.'
    return
  }
  confirmOpen.value = true
}

async function complete() {
  if (completing.value || saving.value) return
  clearTimer()
  completing.value = true
  formError.value = ''
  saveFailed.value = false
  const sent = { ...form }
  try {
    const updated = await encountersApi.update(props.encounter.id, { ...changes.value, status: 'COMPLETED' })
    baseline.value = sent
    confirmOpen.value = false
    toast.success('Đã hoàn thành khám. Nội dung được khóa.')
    emit('saved', updated)
  } catch (err) {
    confirmOpen.value = false
    handleError(err)
  } finally {
    completing.value = false
  }
}

async function loadRecord() {
  if (!props.encounter.medicalRecordId) return
  try {
    const record = await recordsApi.get(props.encounter.medicalRecordId)
    const loaded = toFormValues(props.encounter, record)
    // Chỉ điền ô tóm tắt mà bác sĩ chưa kịp sửa trong lúc chờ tải
    ;['bloodType', ...SUMMARY_FIELDS.map((field) => field.key)].forEach((key) => {
      if (form[key] === baseline.value[key]) {
        form[key] = loaded[key]
        baseline.value = { ...baseline.value, [key]: loaded[key] }
      }
    })
  } catch {
    recordError.value = 'Chưa tải được tóm tắt bệnh án hiện có; các ô bên dưới có thể đang trống dù bệnh án đã có dữ liệu.'
  }
}

watch(dirty, (value) => emit('dirty-change', value))
watch(
  () => JSON.stringify(form),
  () => {
    saveFailed.value = false
    scheduleAutosave()
  }
)

onMounted(loadRecord)
onBeforeUnmount(clearTimer)

defineExpose({ save, dirty })
</script>
