<template>
  <div class="rounded-md border border-dashed border-slate-300 bg-white px-6 py-10 text-center">
    <i v-if="icon" :class="icon" class="text-3xl text-slate-400" aria-hidden="true"></i>
    <h2 class="mt-3 text-base font-semibold text-slate-900">{{ title }}</h2>
    <p v-if="message" class="mx-auto mt-1 max-w-md text-sm text-slate-600">{{ message }}</p>
    <div v-if="$slots.default" class="mt-5">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: 'fa-regular fa-folder-open' },
  title: { type: String, required: true },
  message: { type: String, default: '' }
})
</script>
