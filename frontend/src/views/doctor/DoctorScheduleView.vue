<template>
  <DoctorLayout>
    <PageHeader title="Lịch làm việc" eyebrow="Bác sĩ" description="Tạo ca làm việc và xem trước các slot sẽ sinh. Server vẫn là nguồn chuẩn." />

    <section class="grid gap-4 xl:grid-cols-[420px_1fr]">
      <form class="rounded-md border border-slate-200 bg-white p-5" @submit.prevent="createSchedule">
        <h2 class="font-semibold text-slate-950">Tạo ca mới</h2>
        <div class="mt-4 grid gap-4 sm:grid-cols-2">
          <BaseDatePicker id="schedule-date" v-model="form.workDate" label="Ngày làm việc" required />
          <BaseInput id="slot-minutes" v-model="form.slotMinutes" label="Phút/slot" type="number" required />
          <BaseInput id="schedule-start" v-model="form.startTime" label="Bắt đầu" type="time" required />
          <BaseInput id="schedule-end" v-model="form.endTime" label="Kết thúc" type="time" required />
          <BaseInput id="break-start" v-model="form.breakStart" label="Nghỉ từ" type="time" />
          <BaseInput id="break-end" v-model="form.breakEnd" label="Nghỉ đến" type="time" />
        </div>

        <div class="mt-4 rounded-md bg-slate-50 p-3">
          <p class="text-sm font-semibold text-slate-700">Xem trước slot</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <span v-for="slot in previewSlots" :key="slot" class="rounded border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700">{{ slot }}</span>
            <span v-if="!previewSlots.length" class="text-sm text-slate-500">Nhập giờ hợp lệ để xem trước.</span>
          </div>
        </div>

        <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ formError }}</p>
        <div class="mt-5 flex justify-end">
          <BaseButton type="submit" :loading="saving">Tạo ca</BaseButton>
        </div>
      </form>

      <div>
        <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có ca làm việc">
          <template #cell-time="{ row }">
            {{ formatDate(row.workDate) }} - {{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}
          </template>
        </DataTable>
      </div>
    </section>
  </DoctorLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseDatePicker, BaseInput, DataTable, PageHeader } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { doctorSchedulesApi } from '@/api/doctorSchedules'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatTime } from '@/utils/formatters'

const columns = [
  { key: 'time', label: 'Ca làm việc' },
  { key: 'slotMinutes', label: 'Phút/slot', formatter: (value) => value || '-' },
  { key: 'status', label: 'Trạng thái', formatter: (value) => value || 'ACTIVE' }
]

const form = reactive({ workDate: '', startTime: '08:00', endTime: '11:00', slotMinutes: 30, breakStart: '', breakEnd: '' })
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const { success, error: toastError } = useToast()

function toMinutes(value) {
  if (!value || !/^\d{2}:\d{2}$/.test(value)) return null
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

function fromMinutes(value) {
  const hour = String(Math.floor(value / 60)).padStart(2, '0')
  const minute = String(value % 60).padStart(2, '0')
  return `${hour}:${minute}`
}

const previewSlots = computed(() => {
  const start = toMinutes(form.startTime)
  const end = toMinutes(form.endTime)
  const breakStart = toMinutes(form.breakStart)
  const breakEnd = toMinutes(form.breakEnd)
  const step = Number(form.slotMinutes)
  if (start === null || end === null || !step || end <= start) return []
  const slots = []
  for (let cursor = start; cursor + step <= end; cursor += step) {
    const next = cursor + step
    const overlapsBreak = breakStart !== null && breakEnd !== null && cursor < breakEnd && next > breakStart
    if (!overlapsBreak) slots.push(`${fromMinutes(cursor)}-${fromMinutes(next)}`)
  }
  return slots
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorSchedulesApi.list({ page: 0, size: 20 })
    rows.value = response.content ?? response.items ?? response
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

async function createSchedule() {
  formError.value = ''
  if (!previewSlots.value.length) {
    formError.value = 'Ca làm việc chưa hợp lệ.'
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
    load()
  } catch (err) {
    const message = apiErrorMessage(err)
    formError.value = message
    toastError(message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
