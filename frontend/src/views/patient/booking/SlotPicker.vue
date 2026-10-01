<template>
  <div>
    <p v-if="!doctorId || !date" class="rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">
      Chọn bác sĩ và ngày khám để xem các khung giờ còn trống.
    </p>
    <p v-else-if="isPast" class="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">
      Ngày khám đã qua. Vui lòng chọn ngày từ hôm nay trở đi.
    </p>
    <LoadingSpinner v-else-if="loading" label="Đang tải khung giờ" />
    <div v-else-if="error" class="rounded-md bg-red-50 px-3 py-3 text-sm text-red-700" role="alert">
      <p>{{ error }}</p>
      <BaseButton class="mt-2" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>
    <EmptyState
      v-else-if="!slots.length"
      icon="fa-regular fa-clock"
      title="Không còn khung giờ trống"
      message="Hãy thử chọn ngày khác hoặc bác sĩ khác."
    />
    <ul v-else class="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4" aria-label="Khung giờ còn trống">
      <li v-for="slot in slots" :key="slot.id">
        <button
          type="button"
          class="w-full rounded-md border px-3 py-2 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-primary-600/40"
          :class="
            slot.id === modelValue
              ? 'border-primary-600 bg-primary-600 text-white'
              : 'border-slate-300 bg-white text-slate-800 hover:border-primary-600'
          "
          :aria-pressed="slot.id === modelValue"
          @click="choose(slot)"
        >
          {{ slotLabel(slot) }}
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { BaseButton, EmptyState, LoadingSpinner } from '@/components/ui'
import { slotsApi } from '@/api/slots'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { isPastDate, normalizeList, normalizeSlot, slotLabel } from './bookingUtils'

const props = defineProps({
  doctorId: { type: [Number, String], default: null },
  date: { type: String, default: '' },
  modelValue: { type: [Number, String], default: null },
  /** Tăng giá trị này để tải lại danh sách (ví dụ sau lỗi 409 vì slot vừa bị người khác đặt). */
  refreshKey: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'select', 'loaded'])

const slots = ref([])
const loading = ref(false)
const error = ref('')
const isPast = computed(() => isPastDate(props.date))
let requestId = 0

async function load() {
  slots.value = []
  error.value = ''
  if (!props.doctorId || !props.date || isPast.value) return

  const current = ++requestId
  loading.value = true
  try {
    const response = await slotsApi.availableByDoctor(props.doctorId, props.date)
    if (current !== requestId) return // đã có yêu cầu mới hơn (đổi bác sĩ/ngày), bỏ kết quả cũ
    slots.value = normalizeList(response).map(normalizeSlot)
    if (props.modelValue && !slots.value.some((slot) => slot.id === props.modelValue)) {
      emit('update:modelValue', null)
    }
    emit('loaded', slots.value)
  } catch (err) {
    if (current === requestId) error.value = apiErrorMessage(err)
  } finally {
    if (current === requestId) loading.value = false
  }
}

function choose(slot) {
  emit('update:modelValue', slot.id)
  emit('select', slot)
}

watch(() => [props.doctorId, props.date, props.refreshKey], load, { immediate: true })
</script>
