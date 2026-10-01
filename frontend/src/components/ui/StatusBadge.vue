<template>
  <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold" :class="tone.class">
    <span class="h-1.5 w-1.5 rounded-full" :class="tone.dot"></span>
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: String, required: true },
  label: { type: String, default: '' }
})

const tones = {
  ACTIVE: { class: 'bg-emerald-50 text-emerald-700', dot: 'bg-emerald-600' },
  BOOKED: { class: 'bg-primary-50 text-primary-700', dot: 'bg-primary-600' },
  IN_PROGRESS: { class: 'bg-amber-50 text-amber-700', dot: 'bg-amber-600' },
  COMPLETED: { class: 'bg-slate-100 text-slate-700', dot: 'bg-slate-500' },
  CANCELLED: { class: 'bg-red-50 text-red-700', dot: 'bg-red-600' },
  PAID: { class: 'bg-emerald-50 text-emerald-700', dot: 'bg-emerald-600' },
  UNPAID: { class: 'bg-amber-50 text-amber-700', dot: 'bg-amber-600' },
  VOID: { class: 'bg-slate-100 text-slate-700', dot: 'bg-slate-500' },
  INACTIVE: { class: 'bg-slate-100 text-slate-700', dot: 'bg-slate-500' }
}

const tone = computed(() => tones[props.value] || { class: 'bg-slate-100 text-slate-700', dot: 'bg-slate-500' })
const label = computed(() => props.label || props.value)
</script>
