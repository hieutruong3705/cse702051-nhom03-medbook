<template>
  <span
    v-if="variant === 'icon'"
    :class="['inline-flex shrink-0 items-center justify-center rounded-md', theme.soft, theme.text, sizeClass]"
    aria-hidden="true"
    data-test="specialty-icon"
  >
    <i :class="['fa-solid', theme.icon]"></i>
  </span>
  <span
    v-else
    :class="['inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset', theme.soft, theme.text, theme.ring]"
    data-test="specialty-pill"
  >
    <i :class="['fa-solid', theme.icon]" aria-hidden="true"></i>{{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { specialtyTheme } from '@/utils/specialtyTheme'

const props = defineProps({
  // Chuyên khoa ({code, name}), bác sĩ ({specialtyName}) hoặc tên chuyên khoa
  specialty: { type: [Object, String], default: null },
  // 'icon': ô biểu tượng (thẻ chuyên khoa, đầu trang); 'pill': nhãn chữ nhỏ có biểu tượng (thẻ bác sĩ).
  // Không đặt chú thích HTML ở gốc template để thành phần chỉ có một nút gốc (nhận class từ nơi dùng).
  variant: { type: String, default: 'pill' },
  size: { type: String, default: 'md' }
})

const theme = computed(() => specialtyTheme(props.specialty))
const label = computed(() => {
  const s = props.specialty
  if (!s) return 'Chưa có chuyên khoa'
  return typeof s === 'string' ? s : s.name || s.specialtyName || 'Chưa có chuyên khoa'
})
const SIZES = { sm: 'h-12 w-12 text-xl', md: 'h-14 w-14 text-2xl', lg: 'h-24 w-full text-4xl' }
const sizeClass = computed(() => SIZES[props.size] || SIZES.md)
</script>
