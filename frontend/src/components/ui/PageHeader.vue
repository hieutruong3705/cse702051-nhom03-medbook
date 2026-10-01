<template>
  <div class="flex flex-col gap-4 border-b border-slate-200 bg-white px-4 py-5 sm:px-6 lg:flex-row lg:items-center lg:justify-between">
    <div>
      <p v-if="eyebrow" class="text-sm font-semibold uppercase tracking-wide text-primary-700">{{ eyebrow }}</p>
      <h1 class="text-2xl font-bold text-slate-950">{{ title }}</h1>
      <p v-if="description" class="mt-1 max-w-3xl text-sm text-slate-600">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>
