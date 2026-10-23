<template>
  <FormField :for-id="id" :label="label" :hint="hint" :error="error" :required="required">
    <template #default="{ describedBy, invalid }">
      <div class="relative">
        <input
          :id="id"
          :value="modelValue"
          :type="effectiveType"
          :placeholder="placeholder"
          :autocomplete="autocomplete"
          :disabled="disabled"
          :required="required"
          :aria-describedby="describedBy"
          :aria-invalid="invalid"
          :class="[
            'w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition placeholder:text-slate-400 focus:border-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-600/20 disabled:bg-slate-100',
            canReveal ? 'pr-11' : ''
          ]"
          @input="$emit('update:modelValue', $event.target.value)"
        />
        <button
          v-if="canReveal"
          type="button"
          class="absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-r-md text-slate-500 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary-600 disabled:cursor-not-allowed disabled:text-slate-300"
          :aria-label="passwordVisible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
          :aria-pressed="passwordVisible"
          :title="passwordVisible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
          :disabled="disabled"
          @click="passwordVisible = !passwordVisible"
        >
          <i :class="passwordVisible ? 'fa-solid fa-eye-slash' : 'fa-solid fa-eye'" aria-hidden="true"></i>
        </button>
      </div>
    </template>
  </FormField>
</template>

<script setup>
import { computed, ref } from 'vue'
import FormField from './FormField.vue'

const props = defineProps({
  id: { type: String, required: true },
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, required: true },
  type: { type: String, default: 'text' },
  autocomplete: { type: String, default: undefined },
  placeholder: { type: String, default: '' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  required: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  revealable: { type: Boolean, default: false }
})

const passwordVisible = ref(false)
const canReveal = computed(() => props.revealable && props.type === 'password')
const effectiveType = computed(() => (canReveal.value && passwordVisible.value ? 'text' : props.type))

defineEmits(['update:modelValue'])
</script>
