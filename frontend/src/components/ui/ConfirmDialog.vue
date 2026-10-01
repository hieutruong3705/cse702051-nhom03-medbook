<template>
  <FormModal :model-value="modelValue" :title="title" :description="message" @update:model-value="$emit('update:modelValue', $event)">
    <template #footer>
      <BaseButton variant="secondary" @click="$emit('update:modelValue', false)">Hủy</BaseButton>
      <BaseButton :variant="danger ? 'danger' : 'primary'" :loading="loading" @click="$emit('confirm')">
        {{ confirmText }}
      </BaseButton>
    </template>
  </FormModal>
</template>

<script setup>
import BaseButton from './BaseButton.vue'
import FormModal from './FormModal.vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: 'Xác nhận' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: 'Xác nhận' },
  danger: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

defineEmits(['update:modelValue', 'confirm'])
</script>
