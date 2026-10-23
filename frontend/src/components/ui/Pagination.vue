<template>
  <nav class="flex flex-col gap-3 border-t border-slate-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between" aria-label="Phân trang">
    <p class="text-sm text-slate-600">
      Trang <span class="font-semibold">{{ page + 1 }}</span> / <span class="font-semibold">{{ totalPages || 1 }}</span>
    </p>
    <div class="flex items-center gap-2">
      <button type="button" class="rounded border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-50" :disabled="page <= 0" @click="$emit('update:page', page - 1)">
        Trước
      </button>
      <button type="button" class="rounded border border-slate-300 px-3 py-1.5 text-sm disabled:opacity-50" :disabled="page >= totalPages - 1" @click="$emit('update:page', page + 1)">
        Sau
      </button>
    </div>
  </nav>
</template>

<script setup>
defineProps({
  page: { type: Number, default: 0 },
  totalPages: { type: Number, default: 0 }
})

defineEmits(['update:page'])
</script>
