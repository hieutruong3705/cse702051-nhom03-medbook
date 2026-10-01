<template>
  <section class="space-y-4" aria-labelledby="rx-title">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h2 id="rx-title" class="text-lg font-semibold text-slate-950">Đơn thuốc</h2>
      <BaseButton v-if="editable" variant="secondary" icon="fa-solid fa-plus" :loading="creating" data-test="create-prescription" @click="createPrescription">
        Tạo đơn thuốc
      </BaseButton>
    </div>

    <LoadingSpinner v-if="loading" label="Đang tải đơn thuốc" />

    <div v-else-if="loadError" class="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert" data-test="rx-load-error">
      <p class="font-semibold">{{ loadError }}</p>
      <BaseButton class="mt-3" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>

    <p v-else-if="!prescriptions.length" class="rounded-md border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-500" data-test="rx-empty">
      Chưa có đơn thuốc nào cho lần khám này.
    </p>

    <p v-if="actionError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="rx-action-error">{{ actionError }}</p>

    <article
      v-for="rx in prescriptions"
      :key="rx.id"
      class="rounded-md border border-slate-200 bg-white p-4 sm:p-6"
      :data-test="`prescription-${rx.id}`"
    >
      <header class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 class="font-semibold text-slate-950">Đơn {{ rx.prescriptionCode || `#${rx.id}` }}</h3>
          <p class="text-xs text-slate-500">Kê lúc {{ formatDateTime(rx.issuedAt) }}</p>
        </div>
        <StatusBadge :value="rx.status || 'ACTIVE'" :label="rx.status === 'CANCELLED' ? 'Đã hủy' : 'Hiệu lực'" />
      </header>
      <p v-if="rx.notes" class="mt-2 whitespace-pre-line text-sm text-slate-700">{{ rx.notes }}</p>

      <div class="mt-4 overflow-x-auto">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
          <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
            <tr>
              <th scope="col" class="px-3 py-2">Thuốc</th>
              <th scope="col" class="px-3 py-2">Liều</th>
              <th scope="col" class="px-3 py-2">Tần suất</th>
              <th scope="col" class="px-3 py-2">Số ngày</th>
              <th scope="col" class="px-3 py-2">Số lượng</th>
              <th scope="col" class="px-3 py-2">Hướng dẫn</th>
              <th v-if="editable" scope="col" class="px-3 py-2"><span class="sr-only">Thao tác</span></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-if="!rx.items.length">
              <td :colspan="editable ? 7 : 6" class="px-3 py-4 text-center text-slate-500">Đơn chưa có thuốc nào.</td>
            </tr>
            <tr v-for="item in rx.items" :key="item.id" :data-test="`item-${item.id}`">
              <td class="px-3 py-2 font-medium text-slate-900">{{ item.medicineName }}</td>
              <td class="px-3 py-2">{{ item.dosage || '—' }}</td>
              <td class="px-3 py-2">{{ item.frequency || '—' }}</td>
              <td class="px-3 py-2">{{ item.durationDays ?? '—' }}</td>
              <td class="px-3 py-2">{{ item.quantity }}</td>
              <td class="px-3 py-2">{{ item.instructions || '—' }}</td>
              <td v-if="editable" class="px-3 py-2 text-right">
                <BaseButton size="sm" variant="danger" :aria-label="`Xóa ${item.medicineName}`" @click="askRemove(rx, item)">Xóa</BaseButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <form v-if="editable" class="mt-5 border-t border-slate-100 pt-4" novalidate :data-test="`item-form-${rx.id}`" @submit.prevent="addItem(rx)">
        <h4 class="text-sm font-semibold text-slate-900">Thêm thuốc</h4>
        <div class="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <BaseInput :id="`rx-${rx.id}-name`" v-model="draftFor(rx).medicineName" label="Tên thuốc" required :error="draftFor(rx).errors.medicineName" />
          <BaseInput :id="`rx-${rx.id}-quantity`" v-model="draftFor(rx).quantity" label="Số lượng" type="number" required :error="draftFor(rx).errors.quantity" />
          <BaseInput :id="`rx-${rx.id}-days`" v-model="draftFor(rx).durationDays" label="Số ngày dùng" type="number" :error="draftFor(rx).errors.durationDays" />
          <BaseInput :id="`rx-${rx.id}-dosage`" v-model="draftFor(rx).dosage" label="Liều dùng" placeholder="VD: 1 viên" :error="draftFor(rx).errors.dosage" />
          <BaseInput :id="`rx-${rx.id}-frequency`" v-model="draftFor(rx).frequency" label="Tần suất" placeholder="VD: 2 lần/ngày" :error="draftFor(rx).errors.frequency" />
          <BaseInput :id="`rx-${rx.id}-instructions`" v-model="draftFor(rx).instructions" label="Hướng dẫn" placeholder="VD: Uống sau ăn" :error="draftFor(rx).errors.instructions" />
        </div>
        <p v-if="draftFor(rx).error" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="item-error">{{ draftFor(rx).error }}</p>
        <div class="mt-4 flex justify-end">
          <BaseButton type="submit" icon="fa-solid fa-plus" :loading="draftFor(rx).saving">Thêm vào đơn</BaseButton>
        </div>
      </form>
    </article>

    <ConfirmDialog
      v-model="removeOpen"
      title="Xóa thuốc khỏi đơn?"
      :message="removing ? `Thuốc “${removing.item.medicineName}” sẽ bị xóa khỏi đơn.` : ''"
      confirm-text="Xóa thuốc"
      danger
      :loading="removeBusy"
      @confirm="confirmRemove"
    />
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseInput, ConfirmDialog, LoadingSpinner, StatusBadge } from '@/components/ui'
import { prescriptionItemsApi } from '@/api/prescriptionItems'
import { prescriptionsApi } from '@/api/prescriptions'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'

const props = defineProps({
  encounterId: { type: [Number, String], required: true },
  /** Chỉ true khi bác sĩ phụ trách và lần khám còn OPEN (server quyết định, truyền từ EncounterDTO). */
  editable: { type: Boolean, default: false }
})

const emit = defineEmits(['stale'])
const toast = useToast()

const prescriptions = ref([])
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
const creating = ref(false)
const drafts = reactive({})

const removeOpen = ref(false)
const removing = ref(null)
const removeBusy = ref(false)

const blankDraft = () => ({
  medicineName: '',
  quantity: '',
  durationDays: '',
  dosage: '',
  frequency: '',
  instructions: '',
  errors: {},
  error: '',
  saving: false
})

function draftFor(rx) {
  if (!drafts[rx.id]) drafts[rx.id] = blankDraft()
  return drafts[rx.id]
}

const list = (response) => (Array.isArray(response) ? response : (response?.content ?? []))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const rows = list(await prescriptionsApi.listByEncounter(props.encounterId))
    prescriptions.value = await Promise.all(
      rows.map(async (rx) => ({ ...rx, items: list(await prescriptionItemsApi.listByPrescription(rx.id)) }))
    )
  } catch (err) {
    loadError.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function handleWriteError(err) {
  // 409: lần khám vừa hoàn thành ở nơi khác → khóa lại giao diện
  if (err?.status === 409) emit('stale')
  return apiErrorMessage(err)
}

async function createPrescription() {
  if (creating.value) return
  creating.value = true
  actionError.value = ''
  try {
    const created = await prescriptionsApi.createForEncounter(props.encounterId, {})
    prescriptions.value = [...prescriptions.value, { ...created, items: [] }]
    toast.success('Đã tạo đơn thuốc. Hãy thêm thuốc vào đơn.')
  } catch (err) {
    actionError.value = handleWriteError(err)
  } finally {
    creating.value = false
  }
}

function validate(draft) {
  const errors = {}
  const name = draft.medicineName.trim()
  if (!name) errors.medicineName = 'Phải nhập tên thuốc'
  else if (name.length > 200) errors.medicineName = 'Tên thuốc tối đa 200 ký tự'

  const quantity = Number(draft.quantity)
  if (draft.quantity === '' || !Number.isFinite(quantity) || quantity <= 0) errors.quantity = 'Số lượng phải lớn hơn 0'

  if (draft.durationDays !== '') {
    const days = Number(draft.durationDays)
    if (!Number.isInteger(days) || days <= 0) errors.durationDays = 'Số ngày phải là số nguyên dương'
  }
  if (draft.dosage.length > 100) errors.dosage = 'Liều dùng tối đa 100 ký tự'
  if (draft.frequency.length > 100) errors.frequency = 'Tần suất tối đa 100 ký tự'
  if (draft.instructions.length > 500) errors.instructions = 'Hướng dẫn tối đa 500 ký tự'
  return errors
}

async function addItem(rx) {
  const draft = draftFor(rx)
  if (draft.saving) return
  draft.errors = validate(draft)
  draft.error = ''
  if (Object.keys(draft.errors).length) return

  draft.saving = true
  try {
    const created = await prescriptionItemsApi.create(rx.id, {
      medicineName: draft.medicineName.trim(),
      quantity: Number(draft.quantity),
      durationDays: draft.durationDays === '' ? undefined : Number(draft.durationDays),
      dosage: draft.dosage.trim() || undefined,
      frequency: draft.frequency.trim() || undefined,
      instructions: draft.instructions.trim() || undefined
    })
    rx.items = [...rx.items, created]
    drafts[rx.id] = blankDraft()
    toast.success(`Đã thêm ${created.medicineName} vào đơn`)
  } catch (err) {
    draft.error = handleWriteError(err)
  } finally {
    draft.saving = false
  }
}

function askRemove(rx, item) {
  actionError.value = ''
  removing.value = { rx, item }
  removeOpen.value = true
}

async function confirmRemove() {
  if (!removing.value || removeBusy.value) return
  const { rx, item } = removing.value
  removeBusy.value = true
  try {
    await prescriptionItemsApi.remove(item.id)
    rx.items = rx.items.filter((existing) => existing.id !== item.id)
    toast.success('Đã xóa thuốc khỏi đơn')
  } catch (err) {
    actionError.value = handleWriteError(err)
  } finally {
    removeBusy.value = false
    removeOpen.value = false
    removing.value = null
  }
}

onMounted(load)
</script>
