<template>
  <div class="space-y-1.5">
    <label v-if="label" :for="forId" class="block text-sm font-medium text-slate-700">
      {{ label }} <span v-if="required" class="text-red-600">*</span>
    </label>
    <slot :described-by="describedBy" :invalid="Boolean(error)" />
    <p v-if="hint && !error" :id="hintId" class="text-sm text-slate-500">{{ hint }}</p>
    <p v-if="error" :id="errorId" class="text-sm text-red-600">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  forId: { type: String, default: undefined },
  label: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  required: { type: Boolean, default: false }
})

const hintId = computed(() => props.forId ? `${props.forId}-hint` : undefined)
const errorId = computed(() => props.forId ? `${props.forId}-error` : undefined)
const describedBy = computed(() => props.error ? errorId.value : props.hint ? hintId.value : undefined)
</script>
