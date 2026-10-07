<template>
  <DoctorLayout>
    <PageHeader
      title="Lịch làm việc"
      eyebrow="Bác sĩ"
      description="Tạo ca làm việc để hệ thống sinh giờ trống cho bệnh nhân đặt lịch. Mọi thay đổi chạm vào giờ đã có lịch hẹn đều bị từ chối để không làm mất lịch của bệnh nhân."
    />

    <div class="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-[400px_minmax(0,1fr)]">
      <div class="min-w-0 space-y-4">
        <!-- Tạo ca -->
        <form class="rounded-md border border-slate-200 bg-white p-5" novalidate @submit.prevent="createSchedule">
          <h2 class="font-semibold text-slate-950">Tạo ca mới</h2>
          <div class="mt-4 grid gap-4 sm:grid-cols-2">
            <BaseDatePicker id="schedule-date" v-model="form.workDate" label="Ngày làm việc" required :error="formErrors.workDate" />
            <BaseInput id="slot-minutes" v-model="form.slotMinutes" label="Phút mỗi slot" type="number" required hint="5 đến 120, chia hết cho 5" :error="formErrors.slotMinutes" />
            <BaseInput id="schedule-start" v-model="form.startTime" label="Bắt đầu" type="time" required :error="formErrors.startTime" />
            <BaseInput id="schedule-end" v-model="form.endTime" label="Kết thúc" type="time" required hint="Ca dài tối đa 12 giờ" :error="formErrors.endTime" />
            <BaseInput id="break-start" v-model="form.breakStart" label="Nghỉ từ" type="time" />
            <BaseInput id="break-end" v-model="form.breakEnd" label="Nghỉ đến" type="time" :error="formErrors.breaks" />
          </div>

          <div class="mt-4 rounded-md bg-slate-50 p-3">
            <p class="text-sm font-semibold text-slate-700">Xem trước: {{ previewSlots.length }} slot</p>
            <div class="mt-2 flex max-h-28 flex-wrap gap-2 overflow-y-auto">
              <span v-for="slot in previewSlots" :key="slot" class="rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700">{{ slot }}</span>
              <span v-if="!previewSlots.length" class="text-sm text-slate-500">Nhập giờ hợp lệ để xem trước.</span>
            </div>
          </div>

          <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>
          <div class="mt-5 flex justify-end">
            <BaseButton type="submit" :loading="saving">Tạo ca</BaseButton>
          </div>
        </form>

        <!-- Ngày nghỉ -->
        <section class="rounded-md border border-slate-200 bg-white p-5" aria-labelledby="days-off-title">
          <h2 id="days-off-title" class="font-semibold text-slate-950">Ngày nghỉ</h2>
          <p class="mt-1 text-sm text-slate-600">Đăng ký nghỉ cả ngày sẽ gỡ mọi giờ trống của ngày đó; xóa ngày nghỉ thì giờ trống được sinh lại theo các ca đang có.</p>
          <form class="mt-3 grid gap-3 sm:grid-cols-[1fr_1fr_auto]" novalidate @submit.prevent="addDayOff">
            <BaseDatePicker id="day-off-date" v-model="dayOffForm.date" label="Ngày nghỉ" required :error="dayOffErrors.date" />
            <BaseInput id="day-off-reason" v-model="dayOffForm.reason" label="Lý do" :error="dayOffErrors.reason" />
            <BaseButton type="submit" variant="secondary" class="self-end" :loading="dayOffSaving">Đăng ký nghỉ</BaseButton>
          </form>
          <p v-if="dayOffError" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ dayOffError }}</p>
          <ul v-if="daysOff.length" class="mt-3 divide-y divide-slate-200 text-sm">
            <li v-for="day in daysOff" :key="day.id" class="flex items-center justify-between gap-3 py-2">
              <span><span class="font-medium text-slate-900">{{ formatDate(day.date) }}</span><span v-if="day.reason" class="text-slate-600"> · {{ day.reason }}</span></span>
              <BaseButton variant="ghost" :aria-label="`Xóa ngày nghỉ ${formatDate(day.date)}`" @click="removeDayOff(day)">Xóa</BaseButton>
            </li>
          </ul>
          <p v-else class="mt-3 text-sm text-slate-500">Chưa đăng ký ngày nghỉ nào sắp tới.</p>
        </section>
      </div>

      <!-- Danh sách ca -->
      <section class="min-w-0" aria-labelledby="schedule-list-title">
        <h2 id="schedule-list-title" class="sr-only">Các ca làm việc sắp tới</h2>
        <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có ca làm việc trong 62 ngày tới">
          <template #cell-time="{ row }">
            <p class="font-medium text-slate-900">{{ formatDate(row.workDate) }}</p>
            <p class="text-xs text-slate-600">{{ formatTime(row.startTime) }} – {{ formatTime(row.endTime) }}</p>
          </template>
          <template #cell-slots="{ row }">
            <button type="button" class="text-primary-700 hover:underline" :aria-label="`Xem slot của ca ${formatDate(row.workDate)} ${formatTime(row.startTime)}`" @click="openSlots(row)">
              {{ row.bookedSlots }}/{{ row.totalSlots }} đã đặt
            </button>
          </template>
          <template #cell-breaks="{ row }">
            <ul v-if="row.breaks?.length" class="space-y-1">
              <li v-for="item in row.breaks" :key="item.id" class="flex items-center gap-1 whitespace-nowrap">
                {{ formatTime(item.startTime) }}–{{ formatTime(item.endTime) }}
                <button type="button" class="rounded px-1 text-slate-500 hover:bg-slate-100 hover:text-red-700" :aria-label="`Xóa giờ nghỉ ${formatTime(item.startTime)} của ca ${formatDate(row.workDate)}`" @click="removeBreak(row, item)">
                  <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                </button>
              </li>
            </ul>
            <span v-else class="text-slate-500">Không</span>
          </template>
          <template #cell-actions="{ row }">
            <div class="flex flex-wrap gap-2">
              <BaseButton variant="secondary" :aria-label="`Sửa giờ ca ${formatDate(row.workDate)} ${formatTime(row.startTime)}`" @click="openEdit(row)">Sửa giờ</BaseButton>
              <BaseButton variant="secondary" :aria-label="`Thêm giờ nghỉ cho ca ${formatDate(row.workDate)} ${formatTime(row.startTime)}`" @click="openBreak(row)">Thêm giờ nghỉ</BaseButton>
              <BaseButton variant="danger" :aria-label="`Xóa ca ${formatDate(row.workDate)} ${formatTime(row.startTime)}`" @click="askRemove(row)">Xóa</BaseButton>
            </div>
          </template>
          <template #footer>
            <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
          </template>
        </DataTable>
      </section>
    </div>

    <!-- Sửa giờ ca -->
    <FormModal v-model="editOpen" title="Sửa giờ ca" :description="`Ca ngày ${formatDate(selected?.workDate)}. Không đổi được ngày: muốn đổi ngày hãy xóa ca rồi tạo lại.`">
      <form class="grid gap-4 sm:grid-cols-3" novalidate @submit.prevent="saveEdit">
        <BaseInput id="edit-start" v-model="editForm.startTime" label="Bắt đầu" type="time" required :error="editErrors.startTime" />
        <BaseInput id="edit-end" v-model="editForm.endTime" label="Kết thúc" type="time" required :error="editErrors.endTime" />
        <BaseInput id="edit-minutes" v-model="editForm.slotMinutes" label="Phút mỗi slot" type="number" required :error="editErrors.slotMinutes" />
      </form>
      <p v-if="editError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ editError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="editOpen = false">Hủy</BaseButton>
        <BaseButton :loading="acting" @click="saveEdit">Lưu</BaseButton>
      </template>
    </FormModal>

    <!-- Thêm giờ nghỉ -->
    <FormModal v-model="breakOpen" title="Thêm giờ nghỉ" :description="`Ca ${formatDate(selected?.workDate)}, ${formatTime(selected?.startTime)} – ${formatTime(selected?.endTime)}. Các giờ trống nằm trong giờ nghỉ sẽ bị gỡ.`">
      <form class="grid gap-4 sm:grid-cols-2" novalidate @submit.prevent="saveBreak">
        <BaseInput id="new-break-start" v-model="breakForm.startTime" label="Nghỉ từ" type="time" required />
        <BaseInput id="new-break-end" v-model="breakForm.endTime" label="Nghỉ đến" type="time" required />
        <div class="sm:col-span-2">
          <BaseInput id="new-break-reason" v-model="breakForm.reason" label="Lý do" />
        </div>
      </form>
      <p v-if="breakError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ breakError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="breakOpen = false">Hủy</BaseButton>
        <BaseButton :loading="acting" @click="saveBreak">Thêm</BaseButton>
      </template>
    </FormModal>

    <!-- Slot của ca -->
    <FormModal v-model="slotsOpen" title="Slot của ca" :description="`${formatDate(selected?.workDate)}, ${formatTime(selected?.startTime)} – ${formatTime(selected?.endTime)}`">
      <LoadingSpinner v-if="slotsLoading" label="Đang tải slot" />
      <p v-else-if="slotsError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ slotsError }}</p>
      <p v-else-if="!slots.length" class="text-sm text-slate-500">Ca này hiện không có slot nào (đang là ngày nghỉ, hoặc giờ của ca đã qua).</p>
      <ul v-else class="grid gap-2 sm:grid-cols-2">
        <li v-for="slot in slots" :key="slot.id" class="flex items-center justify-between gap-2 rounded border border-slate-200 px-3 py-2 text-sm">
          <span class="font-medium text-slate-900">{{ formatTime(slot.startTime) }} – {{ formatTime(slot.endTime) }}</span>
          <RouterLink v-if="slot.appointmentId" class="font-semibold text-primary-700 hover:underline" to="/doctor/appointments">Đã đặt · lịch #{{ slot.appointmentId }}</RouterLink>
          <span v-else class="text-emerald-700">Còn trống</span>
        </li>
      </ul>
      <template #footer>
        <BaseButton variant="secondary" @click="slotsOpen = false">Đóng</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="removeOpen"
      title="Xóa ca làm việc?"
      :message="`Ca ${formatDate(selected?.workDate)} ${formatTime(selected?.startTime)} – ${formatTime(selected?.endTime)} và các giờ trống chưa ai đặt sẽ bị xóa. Nếu ca đã có lịch hẹn, hệ thống sẽ từ chối và giữ nguyên ca.`"
      confirm-text="Xóa ca"
      danger
      :loading="acting"
      @confirm="removeSchedule"
    />
  </DoctorLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BaseButton,
  BaseDatePicker,
  BaseInput,
  ConfirmDialog,
  DataTable,
  FormModal,
  LoadingSpinner,
  PageHeader,
  Pagination
} from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { doctorSchedulesApi } from '@/api/doctorSchedules'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatTime } from '@/utils/formatters'
import { fieldErrors } from '@/utils/params'

const columns = [
  { key: 'time', label: 'Ca làm việc' },
  { key: 'slotMinutes', label: 'Phút/slot', formatter: (value) => value || '-' },
  { key: 'slots', label: 'Slot' },
  { key: 'breaks', label: 'Giờ nghỉ' },
  { key: 'actions', label: 'Thao tác' }
]

const form = reactive({ workDate: '', startTime: '08:00', endTime: '11:00', slotMinutes: 30, breakStart: '', breakEnd: '' })
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const formErrors = ref({})
const { page, size, totalPages, setPageResponse } = usePagination({ size: 20 })
const { success, error: toastError } = useToast()

const selected = ref(null)
const acting = ref(false)
const editOpen = ref(false)
const editForm = reactive({ startTime: '', endTime: '', slotMinutes: 30 })
const editError = ref('')
const editErrors = ref({})
const breakOpen = ref(false)
const breakForm = reactive({ startTime: '', endTime: '', reason: '' })
const breakError = ref('')
const removeOpen = ref(false)
const slotsOpen = ref(false)
const slots = ref([])
const slotsLoading = ref(false)
const slotsError = ref('')

const daysOff = ref([])
const dayOffForm = reactive({ date: '', reason: '' })
const dayOffSaving = ref(false)
const dayOffError = ref('')
const dayOffErrors = ref({})

function toMinutes(value) {
  if (!value || !/^\d{2}:\d{2}/.test(value)) return null
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

function fromMinutes(value) {
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
}

// Cùng quy tắc với máy chủ: ô nối tiếp nhau từ giờ bắt đầu, bỏ ô chạm giờ nghỉ, không nhảy lưới sau giờ nghỉ.
const previewSlots = computed(() => {
  const start = toMinutes(form.startTime)
  const end = toMinutes(form.endTime)
  const breakStart = toMinutes(form.breakStart)
  const breakEnd = toMinutes(form.breakEnd)
  const step = Number(form.slotMinutes)
  if (start === null || end === null || !step || step < 5 || end <= start) return []
  const result = []
  for (let cursor = start; cursor + step <= end; cursor += step) {
    const next = cursor + step
    const overlapsBreak = breakStart !== null && breakEnd !== null && cursor < breakEnd && next > breakStart
    if (!overlapsBreak) result.push(`${fromMinutes(cursor)}-${fromMinutes(next)}`)
  }
  return result
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorSchedulesApi.list({ page: page.value, size: size.value })
    rows.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    rows.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

async function loadDaysOff() {
  try {
    daysOff.value = (await doctorSchedulesApi.daysOff()) ?? []
  } catch {
    daysOff.value = []
  }
}

function changePage(next) {
  page.value = next
  load()
}

async function createSchedule() {
  formError.value = ''
  formErrors.value = {}
  if (!form.workDate) {
    formErrors.value = { workDate: 'Phải chọn ngày làm việc' }
    return
  }
  if (!previewSlots.value.length) {
    formError.value = 'Ca làm việc chưa hợp lệ: giờ kết thúc phải sau giờ bắt đầu và đủ dài cho ít nhất một slot.'
    return
  }
  saving.value = true
  try {
    const breaks = form.breakStart && form.breakEnd ? [{ startTime: form.breakStart, endTime: form.breakEnd }] : []
    await doctorSchedulesApi.create({
      workDate: form.workDate,
      startTime: form.startTime,
      endTime: form.endTime,
      slotMinutes: Number(form.slotMinutes),
      breaks
    })
    success('Đã tạo ca làm việc')
    await load()
  } catch (err) {
    // 422: ca chồng giờ với ca khác hoặc trùng ngày nghỉ; 400: dữ liệu sai theo từng trường.
    formErrors.value = fieldErrors(err)
    formError.value = apiErrorMessage(err)
    toastError(formError.value)
  } finally {
    saving.value = false
  }
}

function openEdit(row) {
  selected.value = row
  Object.assign(editForm, { startTime: formatTime(row.startTime), endTime: formatTime(row.endTime), slotMinutes: row.slotMinutes || 30 })
  editError.value = ''
  editErrors.value = {}
  editOpen.value = true
}

async function saveEdit() {
  if (acting.value) return
  acting.value = true
  editError.value = ''
  editErrors.value = {}
  try {
    await doctorSchedulesApi.update(selected.value.id, {
      startTime: editForm.startTime,
      endTime: editForm.endTime,
      slotMinutes: Number(editForm.slotMinutes)
    })
    success('Đã cập nhật ca làm việc')
    editOpen.value = false
    await load()
  } catch (err) {
    // 409: trong ca đã có lịch hẹn được đặt nên không sửa được.
    editErrors.value = fieldErrors(err)
    editError.value = apiErrorMessage(err)
  } finally {
    acting.value = false
  }
}

function openBreak(row) {
  selected.value = row
  Object.assign(breakForm, { startTime: '', endTime: '', reason: '' })
  breakError.value = ''
  breakOpen.value = true
}

async function saveBreak() {
  if (acting.value) return
  if (!breakForm.startTime || !breakForm.endTime) {
    breakError.value = 'Phải nhập giờ bắt đầu và giờ kết thúc nghỉ.'
    return
  }
  acting.value = true
  breakError.value = ''
  try {
    await doctorSchedulesApi.addBreak(selected.value.id, {
      startTime: breakForm.startTime,
      endTime: breakForm.endTime,
      reason: breakForm.reason.trim()
    })
    success('Đã thêm giờ nghỉ')
    breakOpen.value = false
    await load()
  } catch (err) {
    breakError.value = apiErrorMessage(err)
  } finally {
    acting.value = false
  }
}

async function removeBreak(row, item) {
  try {
    await doctorSchedulesApi.removeBreak(row.id, item.id)
    success('Đã xóa giờ nghỉ, các giờ trống được sinh lại')
    await load()
  } catch (err) {
    toastError(apiErrorMessage(err))
  }
}

function askRemove(row) {
  selected.value = row
  removeOpen.value = true
}

async function removeSchedule() {
  if (acting.value) return
  acting.value = true
  try {
    await doctorSchedulesApi.remove(selected.value.id)
    success('Đã xóa ca làm việc')
  } catch (err) {
    // 409: ca đã có lịch hẹn. Báo đúng lý do của máy chủ; ca được giữ nguyên.
    toastError(apiErrorMessage(err), 'Không xóa được ca')
  } finally {
    acting.value = false
    removeOpen.value = false
    await load()
  }
}

async function openSlots(row) {
  selected.value = row
  slots.value = []
  slotsError.value = ''
  slotsLoading.value = true
  slotsOpen.value = true
  try {
    slots.value = (await doctorSchedulesApi.slots(row.id)) ?? []
  } catch (err) {
    slotsError.value = apiErrorMessage(err)
  } finally {
    slotsLoading.value = false
  }
}

async function addDayOff() {
  dayOffError.value = ''
  dayOffErrors.value = {}
  if (!dayOffForm.date) {
    dayOffErrors.value = { date: 'Phải chọn ngày nghỉ' }
    return
  }
  dayOffSaving.value = true
  try {
    await doctorSchedulesApi.addDayOff({ date: dayOffForm.date, reason: dayOffForm.reason.trim() })
    success('Đã đăng ký ngày nghỉ')
    Object.assign(dayOffForm, { date: '', reason: '' })
    await Promise.all([loadDaysOff(), load()])
  } catch (err) {
    // 409: ngày đó đã có lịch hẹn; 422: đã đăng ký nghỉ ngày này rồi.
    dayOffErrors.value = fieldErrors(err)
    dayOffError.value = apiErrorMessage(err)
  } finally {
    dayOffSaving.value = false
  }
}

async function removeDayOff(day) {
  try {
    await doctorSchedulesApi.removeDayOff(day.id)
    success('Đã xóa ngày nghỉ, giờ trống được sinh lại')
    await Promise.all([loadDaysOff(), load()])
  } catch (err) {
    toastError(apiErrorMessage(err))
  }
}

onMounted(() => {
  load()
  loadDaysOff()
})
</script>
