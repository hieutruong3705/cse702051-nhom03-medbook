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

    <div v-else class="grid grid-cols-[auto_1fr] gap-x-2" :class="showTotals ? 'mt-7' : 'mt-3'" role="img" :aria-label="summary">
      <!-- Trục tung: ba mốc 0, một nửa và giá trị lớn nhất đã làm tròn -->
      <div class="flex h-48 flex-col justify-between text-right text-[11px] leading-none text-slate-500" aria-hidden="true">
        <span v-for="tick in ticks" :key="tick">{{ axisFormat(tick) }}</span>
      </div>
      <div class="relative h-48 border-b border-slate-300">
        <div class="pointer-events-none absolute inset-0 flex flex-col justify-between" aria-hidden="true">
          <span class="border-t border-dashed border-slate-200"></span>
          <span class="border-t border-dashed border-slate-200"></span>
          <span></span>
        </div>
        <div class="absolute inset-0 flex items-end gap-[3px] px-1">
          <div
            v-for="column in columns"
            :key="column.label"
            class="chart-column flex h-full min-w-0 max-w-[56px] flex-1 flex-col justify-end"
            :title="column.tooltip"
          >
            <div class="relative flex flex-col-reverse gap-0.5" :style="{ height: column.height }">
              <span
                v-if="showTotals && column.total > 0"
                class="absolute inset-x-0 bottom-full mb-0.5 whitespace-nowrap text-center text-[11px] font-medium tabular-nums text-slate-700"
              >
                {{ axisFormat(column.total) }}
              </span>
              <template v-for="segment in column.segments" :key="segment.key">
                <span
                  v-if="segment.value > 0"
                  class="min-h-[2px] w-full last:rounded-t"
                  :style="{ flexGrow: segment.value, flexBasis: 0, backgroundColor: segment.color }"
                ></span>
              </template>
            </div>
          </div>
        </div>
      </div>
      <!-- Trục hoành: khi có nhiều cột chỉ ghi nhãn cách quãng để không đè lên nhau -->
      <span></span>
      <div class="flex gap-[3px] px-1 pt-1 text-[11px] text-slate-600" aria-hidden="true">
        <span v-for="(column, index) in columns" :key="column.label" class="min-w-0 max-w-[56px] flex-1 text-center">
          <span v-if="index % labelStep === 0" class="inline-block whitespace-nowrap">{{ column.shortLabel }}</span>
        </span>
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
            <tr v-for="column in columns" :key="column.label">
              <th scope="row" class="px-2 py-1.5 font-medium text-slate-800">{{ column.label }}</th>
              <td v-for="segment in column.segments" :key="segment.key" class="px-2 py-1.5 text-right tabular-nums">
                {{ format(segment.value) }}
              </td>
              <td v-if="series.length > 1" class="px-2 py-1.5 text-right font-medium tabular-nums">{{ format(column.total) }}</td>
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
 * Biểu đồ cột đứng cho số liệu theo thời gian (ngày, tháng, năm), xếp chồng nhiều chuỗi trên một trục. Có chú giải
 * khi nhiều hơn một chuỗi, rê chuột vào cột để xem chi tiết và luôn kèm bảng số liệu.
 *
 * rows:   [{ label, values: { [series.key]: number } }] theo thứ tự thời gian tăng dần.
 * series: [{ key, label }] - thứ tự cố định quyết định màu của từng chuỗi.
 */
const props = defineProps({
  title: { type: String, required: true },
  rows: { type: Array, default: () => [] },
  series: { type: Array, required: true },
  labelHeader: { type: String, default: 'Kỳ' },
  emptyText: { type: String, default: 'Không có dữ liệu trong khoảng thời gian này.' },
  format: { type: Function, default: (value) => new Intl.NumberFormat('vi-VN').format(value) }
})

// Ba màu đầu của bảng màu phân loại đã kiểm tra, cùng thứ tự với biểu đồ thanh ngang và biểu đồ tròn.
const PALETTE = ['#2a78d6', '#eb6834', '#1baf7a']
const color = (index) => PALETTE[index % PALETTE.length]
const MAX_LABELS = 8
const MAX_COLUMNS_WITH_TOTALS = 12

const compact = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 })
const axisFormat = (value) => compact.format(value)

// Làm tròn giá trị lớn nhất lên mốc chẵn (2, 4, 6, 8, 10 nhân lũy thừa của 10) để mốc giữa trục tung cũng là số
// tròn, kể cả khi đếm số lượng nhỏ.
function niceMax(value) {
  if (value <= 2) return 2
  const power = 10 ** Math.floor(Math.log10(value))
  const step = [1, 2, 4, 6, 8, 10].find((candidate) => candidate * power >= value)
  return step * power
}

const totals = computed(() => props.rows.map((row) => props.series.reduce((sum, item) => sum + Number(row.values?.[item.key] || 0), 0)))
const axisMax = computed(() => niceMax(Math.max(...totals.value, 0)))
const ticks = computed(() => [axisMax.value, axisMax.value / 2, 0])
const labelStep = computed(() => Math.max(1, Math.ceil(props.rows.length / MAX_LABELS)))
const showTotals = computed(() => props.rows.length <= MAX_COLUMNS_WITH_TOTALS)

const columns = computed(() =>
  props.rows.map((row, rowIndex) => {
    const segments = props.series.map((item, index) => ({
      key: item.key,
      label: item.label,
      value: Number(row.values?.[item.key] || 0),
      color: color(index)
    }))
    const total = totals.value[rowIndex]
    const detail = segments.map((segment) => `${segment.label}: ${props.format(segment.value)}`).join(' · ')
    return {
      label: row.label,
      // nhãn ngày dd/MM/yyyy chỉ ghi dd/MM dưới cột; năm đã có trong bộ lọc và bảng số liệu
      shortLabel: /^\d{2}\/\d{2}\/\d{4}$/.test(row.label) ? row.label.slice(0, 5) : row.label,
      total,
      segments,
      height: `${(total / axisMax.value) * 100}%`,
      tooltip: props.series.length > 1 ? `${row.label} · ${detail} · Tổng: ${props.format(total)}` : `${row.label}: ${props.format(total)}`
    }
  })
)

const summary = computed(() => {
  const parts = columns.value.slice(0, 12).map((column) => `${column.label}: ${props.format(column.total)}`)
  return `${props.title}. ${parts.join('; ')}`
})
</script>

<style scoped>
.chart-column {
  transition: filter 0.15s ease;
}

.chart-column:hover {
  filter: brightness(0.9);
}
</style>
