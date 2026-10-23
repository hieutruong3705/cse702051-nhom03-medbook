<template>
  <FormField :for-id="id" :label="label" :hint="hint" :error="error" :required="required">
    <template #default="{ describedBy, invalid }">
      <textarea
        :id="id"
        :value="modelValue"
        :rows="rows"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :aria-describedby="describedBy"
        :aria-invalid="invalid"
        class="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition placeholder:text-slate-400 focus:border-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-600/20 disabled:bg-slate-100"
        @input="$emit('update:modelValue', $event.target.value)"
      ></textarea>
    </template>
  </FormField>
</template>

<script setup>
import FormField from './FormField.vue'

defineProps({
  id: { type: String, required: true },
  modelValue: { type: String, default: '' },
  label: { type: String, required: true },
  rows: { type: Number, default: 4 },
  placeholder: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])
</script>
