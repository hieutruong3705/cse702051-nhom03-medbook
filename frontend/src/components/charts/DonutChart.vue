<template>
  <figure class="viz-root">
    <figcaption class="text-sm font-semibold text-slate-900">{{ title }}</figcaption>

    <p v-if="!total" class="mt-3 text-sm text-slate-500">{{ emptyText }}</p>

    <div v-else class="mt-3 flex flex-wrap items-center gap-x-6 gap-y-4">
      <svg class="h-40 w-40 shrink-0" viewBox="0 0 120 120" role="img" :aria-label="summary">
        <g transform="rotate(-90 60 60)">
          <circle
            v-for="slice in slices"
            :key="slice.key"
            class="donut-slice"
            cx="60"
            cy="60"
            :r="RADIUS"
            fill="none"
            :stroke="slice.color"
            :stroke-width="THICKNESS"
            :stroke-dasharray="slice.dash"
            :stroke-dashoffset="slice.offset"
          >
            <title>{{ slice.label }}: {{ format(slice.value) }} ({{ slice.percent }})</title>
          </circle>
        </g>
        <text x="60" y="56" text-anchor="middle" class="fill-slate-500 text-[9px]">{{ centerLabel }}</text>
        <text x="60" y="70" text-anchor="middle" class="fill-slate-950 text-[12px] font-bold">{{ centerValue || format(total) }}</text>
      </svg>

      <ul class="min-w-[220px] flex-1 space-y-1.5 text-sm" aria-label="Chú giải">
        <li v-for="slice in slices" :key="slice.key" class="grid grid-cols-[auto_1fr_auto_auto] items-center gap-x-2">
          <span class="h-2.5 w-2.5 rounded-sm" :style="{ backgroundColor: slice.color }" aria-hidden="true"></span>
          <span class="truncate text-slate-700" :title="slice.label">{{ slice.label }}</span>
          <span class="whitespace-nowrap text-right tabular-nums text-slate-900">{{ format(slice.value) }}</span>
          <span class="w-14 whitespace-nowrap text-right font-medium tabular-nums text-slate-900">{{ slice.percent }}</span>
        </li>
      </ul>
    </div>
  </figure>
</template>

<script setup>
import { computed } from 'vue'

/**
 * Biểu đồ tròn dạng vành khuyên cho tỷ trọng của từng phần trong một tổng. Mỗi phần có dòng chú giải ghi tên, giá
 * trị và phần trăm, nên không thông tin nào chỉ được truyền bằng màu; giữa các phần có khe hở để phân tách.
 *
 * segments: [{ key, label, value, colorIndex? }] - colorIndex cố định màu theo đối tượng; bỏ trống thì theo thứ tự.
 * Nhiều hơn maxSlices phần thì các phần nhỏ nhất được gộp vào "Khác".
 */
const props = defineProps({
  title: { type: String, required: true },
  segments: { type: Array, default: () => [] },
  centerLabel: { type: String, default: 'Tổng' },
  centerValue: { type: String, default: '' },
  maxSlices: { type: Number, default: 6 },
  emptyText: { type: String, default: 'Không có dữ liệu trong khoảng thời gian này.' },
  format: { type: Function, default: (value) => new Intl.NumberFormat('vi-VN').format(value) }
})

// Sáu màu đầu của bảng màu phân loại đã kiểm tra (cùng thứ tự với biểu đồ cột), màu trung tính cho phần "Khác".
const PALETTE = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300']
const OTHER_COLOR = '#898781'
const RADIUS = 45
const THICKNESS = 22
const CIRCUMFERENCE = 2 * Math.PI * RADIUS
const GAP = 1.5

const percentFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 })

const visible = computed(() => {
  const positive = props.segments
    .map((segment, index) => ({ ...segment, value: Number(segment.value || 0), colorIndex: segment.colorIndex ?? index }))
    .filter((segment) => segment.value > 0)
  if (positive.length <= props.maxSlices) return positive
  const ranked = [...positive].sort((a, b) => b.value - a.value)
  const kept = new Set(ranked.slice(0, props.maxSlices - 1).map((segment) => segment.key))
  const other = positive.filter((segment) => !kept.has(segment.key)).reduce((sum, segment) => sum + segment.value, 0)
  return [...positive.filter((segment) => kept.has(segment.key)), { key: '__other', label: 'Khác', value: other, colorIndex: -1 }]
})

const total = computed(() => visible.value.reduce((sum, segment) => sum + segment.value, 0))

const slices = computed(() => {
  const gap = visible.value.length > 1 ? GAP : 0
  let start = 0
  return visible.value.map((segment) => {
    const length = (segment.value / total.value) * CIRCUMFERENCE
    const drawn = Math.max(length - gap, 0.5)
    const slice = {
      key: segment.key,
      label: segment.label,
      value: segment.value,
      color: segment.colorIndex < 0 ? OTHER_COLOR : PALETTE[segment.colorIndex % PALETTE.length],
      dash: `${drawn} ${CIRCUMFERENCE - drawn}`,
      offset: -start,
      percent: `${percentFormat.format((segment.value / total.value) * 100)}%`
    }
    start += length
    return slice
  })
})

const summary = computed(
  () => `${props.title}. ${slices.value.map((slice) => `${slice.label}: ${props.format(slice.value)} (${slice.percent})`).join('; ')}`
)
</script>

<style scoped>
.donut-slice {
  transition: opacity 0.15s ease;
}

svg:hover .donut-slice:not(:hover) {
  opacity: 0.55;
}
</style>
