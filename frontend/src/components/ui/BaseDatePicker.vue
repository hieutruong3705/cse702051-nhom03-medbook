<template>
  <BaseInput
    :id="id"
    :model-value="modelValue"
    :label="label"
    type="date"
    :hint="hint"
    :error="error"
    :required="required"
    :disabled="disabled"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>

<script setup>
import BaseInput from './BaseInput.vue'

defineProps({
  id: { type: String, required: true },
  modelValue: { type: String, default: '' },
  label: { type: String, required: true },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])
</script>
