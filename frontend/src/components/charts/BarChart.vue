<template>
  <figure class="viz-root">
    <figcaption class="flex flex-wrap items-center justify-between gap-2">
      <span class="text-sm font-semibold text-slate-900">{{ title }}</span>
      <ul v-if="series.length > 1" class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-700" aria-label="Chú giải">
        <li v-for="(item, index) in series" :key="item.key" class="inline-flex items-center gap-1.5">
          <span class="h-2.5 w-2.5 rounded-sm" :style="{ backgroundColor: color(index) }" aria-hidden="true"></span>
          {{ item.label }}
        </li>
      </ul>
    </figcaption>

    <p v-if="!rows.length" class="mt-3 text-sm text-slate-500">{{ emptyText }}</p>

    <div v-else class="mt-3 space-y-2" role="img" :aria-label="summary">
      <div v-for="row in bars" :key="row.label" class="grid grid-cols-[minmax(72px,30%)_1fr_auto] items-center gap-2 text-sm">
        <span class="truncate text-slate-700" :title="row.label">{{ row.label }}</span>
        <div class="flex h-4 items-stretch gap-0.5 rounded bg-slate-100">
          <template v-for="segment in row.segments" :key="segment.key">
            <span
              v-if="segment.value > 0"
              class="chart-segment min-w-[3px] first:rounded-l last:rounded-r"
              :style="{ width: segment.width, backgroundColor: segment.color }"
              :title="`${row.label} · ${segment.label}: ${format(segment.value)}`"
            ></span>
          </template>
        </div>
        <span class="whitespace-nowrap text-right font-medium tabular-nums text-slate-900">{{ format(row.total) }}</span>
      </div>
    </div>

    <details v-if="rows.length" class="mt-3 text-sm">
      <summary class="cursor-pointer text-primary-700 hover:underline">Xem bảng số liệu</summary>
      <div class="mt-2 overflow-x-auto">
        <table class="min-w-full divide-y divide-slate-200 text-left">
          <thead>
            <tr class="text-xs uppercase tracking-wide text-slate-600">
              <th scope="col" class="px-2 py-1.5">{{ labelHeader }}</th>
              <th v-for="item in series" :key="item.key" scope="col" class="px-2 py-1.5 text-right">{{ item.label }}</th>
              <th v-if="series.length > 1" scope="col" class="px-2 py-1.5 text-right">Tổng</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="row in bars" :key="row.label">
              <th scope="row" class="px-2 py-1.5 font-medium text-slate-800">{{ row.label }}</th>
              <td v-for="segment in row.segments" :key="segment.key" class="px-2 py-1.5 text-right tabular-nums">
                {{ format(segment.value) }}
              </td>
              <td v-if="series.length > 1" class="px-2 py-1.5 text-right font-medium tabular-nums">{{ format(row.total) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </figure>
</template>

<script setup>
import { computed } from 'vue'

/**
 * Biểu đồ cột ngang, có thể xếp chồng nhiều chuỗi trên một trục. Mỗi thanh có nhãn và giá trị tổng ghi sẵn; có chú
 * giải khi nhiều hơn một chuỗi và luôn kèm bảng số liệu, nên không thông tin nào chỉ được truyền bằng màu.
 *
 * rows:   [{ label, values: { [series.key]: number } }]
 * series: [{ key, label }] - thứ tự cố định quyết định màu của từng chuỗi.
 */
const props = defineProps({
  title: { type: String, required: true },
  rows: { type: Array, default: () => [] },
  series: { type: Array, required: true },
  labelHeader: { type: String, default: 'Nhóm' },
  emptyText: { type: String, default: 'Không có dữ liệu trong khoảng thời gian này.' },
  format: { type: Function, default: (value) => new Intl.NumberFormat('vi-VN').format(value) }
})

// Ba màu đầu của bảng màu phân loại đã kiểm tra (phân biệt được với người mù màu, cách nhau trên nền sáng).
const PALETTE = ['#2a78d6', '#eb6834', '#1baf7a']
const color = (index) => PALETTE[index % PALETTE.length]

const bars = computed(() => {
  const totals = props.rows.map((row) => props.series.reduce((sum, item) => sum + Number(row.values?.[item.key] || 0), 0))
  const max = Math.max(...totals, 0)
  return props.rows.map((row, rowIndex) => ({
    label: row.label,
    total: totals[rowIndex],
    segments: props.series.map((item, index) => {
      const value = Number(row.values?.[item.key] || 0)
      return {
        key: item.key,
        label: item.label,
        value,
        color: color(index),
        width: max > 0 ? `${(value / max) * 100}%` : '0%'
      }
    })
  }))
})

const summary = computed(() => {
  const parts = bars.value.slice(0, 12).map((row) => `${row.label}: ${props.format(row.total)}`)
  return `${props.title}. ${parts.join('; ')}`
})
</script>

<style scoped>
.chart-segment {
  transition: filter 0.15s ease;
}

.chart-segment:hover {
  filter: brightness(0.9);
}
</style>
