<template>
  <!-- Chế độ nhập: nhóm radio thật (dùng được bằng phím mũi tên, trình đọc màn hình đọc "4 sao trên 5") -->
  <fieldset v-if="editable" class="min-w-0">
    <legend class="mb-1 block text-sm font-medium text-slate-700">
      {{ label }}<span v-if="required" class="text-red-700" aria-hidden="true"> *</span>
    </legend>
    <div class="flex items-center gap-1" :aria-describedby="error ? `${id}-error` : undefined">
      <label
        v-for="star in 5"
        :key="star"
        class="cursor-pointer rounded p-1 text-2xl focus-within:ring-2 focus-within:ring-primary-600"
        :class="star <= (hovered || modelValue || 0) ? 'text-amber-500' : 'text-slate-300'"
        @mouseenter="hovered = star"
        @mouseleave="hovered = 0"
      >
        <input
          :id="`${id}-${star}`"
          class="sr-only"
          type="radio"
          :name="id"
          :value="star"
          :checked="modelValue === star"
          :required="required"
          @change="$emit('update:modelValue', star)"
        />
        <i class="fa-solid fa-star" aria-hidden="true"></i>
        <span class="sr-only">{{ star }} sao trên 5</span>
      </label>
      <span class="ml-2 text-sm text-slate-700" aria-live="polite">{{ modelValue ? `${modelValue}/5 · ${WORDS[modelValue]}` : 'Chưa chọn' }}</span>
    </div>
    <p v-if="error" :id="`${id}-error`" class="mt-1 text-sm text-red-700">{{ error }}</p>
  </fieldset>

  <!-- Chế độ hiển thị: chỉ là hình, có nhãn văn bản cho trình đọc màn hình -->
  <span v-else class="inline-flex items-center gap-1" role="img" :aria-label="displayLabel">
    <i
      v-for="star in 5"
      :key="star"
      aria-hidden="true"
      :class="[starIcon(star).icon, sizeClass, starIcon(star).on ? 'text-amber-500' : 'text-slate-300']"
    ></i>
    <span v-if="showValue" class="ml-1 text-sm font-semibold text-slate-800" aria-hidden="true">
      {{ value ? Number(value).toFixed(1) : '–' }}
    </span>
  </span>
</template>

<script setup>
import { computed, ref } from 'vue'

const WORDS = { 1: 'Rất không hài lòng', 2: 'Không hài lòng', 3: 'Bình thường', 4: 'Hài lòng', 5: 'Rất hài lòng' }

const props = defineProps({
  editable: { type: Boolean, default: false },
  id: { type: String, default: 'rating' },
  label: { type: String, default: 'Mức độ hài lòng' },
  modelValue: { type: Number, default: 0 },
  value: { type: Number, default: null },
  required: { type: Boolean, default: false },
  error: { type: String, default: '' },
  showValue: { type: Boolean, default: false },
  size: { type: String, default: 'sm' }
})

defineEmits(['update:modelValue'])

const hovered = ref(0)
const sizeClass = computed(() => (props.size === 'lg' ? 'text-xl' : 'text-sm'))
const displayLabel = computed(() =>
  props.value ? `${Number(props.value).toFixed(1)} trên 5 sao` : 'Chưa có đánh giá'
)

// Sao đầy cho phần nguyên, nửa sao khi phần lẻ từ 0,5 trở lên (4,5 hiện khác 4,0), còn lại là sao xám
function starIcon(star) {
  const value = props.value || 0
  if (star <= Math.floor(value)) return { icon: 'fa-solid fa-star', on: true }
  if (star - value <= 0.5) return { icon: 'fa-solid fa-star-half-stroke', on: true }
  return { icon: 'fa-solid fa-star', on: false }
}
</script>
