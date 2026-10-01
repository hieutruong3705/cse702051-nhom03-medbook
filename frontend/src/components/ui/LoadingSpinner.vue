<template>
  <span class="inline-flex items-center gap-2 text-sm text-slate-600" role="status">
    <i class="fa-solid fa-spinner animate-spin text-primary-600" aria-hidden="true"></i>
    <span>{{ label }}</span>
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, default: 'Đang tải' }
})
</script>
