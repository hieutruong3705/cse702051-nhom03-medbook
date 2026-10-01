<template>
  <div class="overflow-hidden rounded-md border border-slate-200 bg-white">
    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-slate-200">
        <thead class="bg-slate-50">
          <tr>
            <th
              v-for="column in columns"
              :key="column.key"
              scope="col"
              class="whitespace-nowrap px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-600"
            >
              <button
                v-if="column.sortable"
                type="button"
                class="inline-flex items-center gap-1 hover:text-primary-700"
                @click="$emit('sort', column.key)"
              >
                {{ column.label }}
                <i class="fa-solid fa-sort text-slate-400" aria-hidden="true"></i>
              </button>
              <span v-else>{{ column.label }}</span>
            </th>
          </tr>
        </thead>
        <tbody v-if="loading" class="divide-y divide-slate-200">
          <tr v-for="index in skeletonRows" :key="index">
            <td v-for="column in columns" :key="column.key" class="px-4 py-3">
              <div class="h-3 w-3/4 animate-pulse rounded bg-slate-200"></div>
            </td>
          </tr>
        </tbody>
        <tbody v-else-if="error" class="divide-y divide-slate-200">
          <tr>
            <td :colspan="columns.length" class="px-4 py-10 text-center text-sm text-red-600">{{ error }}</td>
          </tr>
        </tbody>
        <tbody v-else-if="!rows.length" class="divide-y divide-slate-200">
          <tr>
            <td :colspan="columns.length" class="px-4 py-10 text-center text-sm text-slate-500">{{ emptyText }}</td>
          </tr>
        </tbody>
        <tbody v-else class="divide-y divide-slate-200">
          <tr v-for="(row, rowIndex) in rows" :key="row[rowKey] ?? rowIndex" class="hover:bg-slate-50">
            <td v-for="column in columns" :key="column.key" class="px-4 py-3 text-sm text-slate-700">
              <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">
                {{ column.formatter ? column.formatter(row[column.key], row) : row[column.key] }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <slot name="footer" />
  </div>
</template>

<script setup>
defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyText: { type: String, default: 'Không có dữ liệu' },
  skeletonRows: { type: Number, default: 4 }
})

defineEmits(['sort'])
</script>
