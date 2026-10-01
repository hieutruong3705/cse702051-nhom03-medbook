<template>
  <div class="animate-pulse space-y-3" aria-hidden="true">
    <div v-for="line in lines" :key="line" class="h-3 rounded bg-slate-200" :style="{ width: widths[(line - 1) % widths.length] }"></div>
  </div>
</template>

<script setup>
defineProps({
  lines: { type: Number, default: 3 },
  widths: { type: Array, default: () => ['100%', '86%', '70%'] }
})
</script>
