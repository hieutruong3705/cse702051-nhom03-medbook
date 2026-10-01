<template>
  <FormField :for-id="id" :label="label" :hint="hintText" :error="errorMessage" :required="required">
    <template #default="{ describedBy, invalid }">
      <input
        :id="id"
        ref="inputRef"
        type="file"
        :accept="accept"
        :required="required"
        :aria-describedby="describedBy"
        :aria-invalid="invalid"
        class="block w-full rounded-md border border-dashed border-slate-300 bg-white px-3 py-4 text-sm text-slate-700 file:mr-4 file:rounded-md file:border-0 file:bg-primary-50 file:px-3 file:py-2 file:text-sm file:font-semibold file:text-primary-700 hover:border-primary-500"
        @change="handleChange"
      />
    </template>
  </FormField>
</template>

<script setup>
import { computed, ref } from 'vue'
import FormField from './FormField.vue'

const props = defineProps({
  id: { type: String, required: true },
  label: { type: String, default: 'Tệp đính kèm' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  required: { type: Boolean, default: false },
  maxBytes: { type: Number, default: 10 * 1024 * 1024 },
  accept: { type: String, default: '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png' }
})

const emit = defineEmits(['update:file', 'invalid'])
const inputRef = ref(null)
const localError = ref('')
const allowedTypes = ['application/pdf', 'image/jpeg', 'image/png']

const errorMessage = computed(() => props.error || localError.value)
const hintText = computed(() => props.hint || 'PDF, JPG hoặc PNG, tối đa 10 MB.')

const handleChange = (event) => {
  localError.value = ''
  const file = event.target.files?.[0]
  if (!file) {
    emit('update:file', null)
    return
  }
  if (!allowedTypes.includes(file.type)) {
    localError.value = 'Chỉ hỗ trợ PDF, JPG hoặc PNG.'
  } else if (file.size > props.maxBytes) {
    localError.value = 'Tệp vượt quá giới hạn 10 MB.'
  }
  if (localError.value) {
    inputRef.value.value = ''
    emit('invalid', localError.value)
    emit('update:file', null)
    return
  }
  emit('update:file', file)
}
</script>
