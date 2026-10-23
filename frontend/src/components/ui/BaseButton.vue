<template>
  <component
    :is="href ? 'a' : 'button'"
    :href="href"
    :type="href ? undefined : type"
    :disabled="isDisabled"
    class="inline-flex min-h-10 items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2"
    :class="[variantClass, isDisabled ? 'cursor-not-allowed opacity-60' : '']"
    @click="handleClick"
  >
    <i v-if="icon && !loading" :class="icon" aria-hidden="true"></i>
    <i v-if="loading" class="fa-solid fa-spinner animate-spin" aria-hidden="true"></i>
    <span><slot /></span>
  </component>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: 'button' },
  variant: { type: String, default: 'primary' },
  icon: { type: String, default: '' },
  href: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

const isDisabled = computed(() => props.disabled || props.loading)
const variantClass = computed(() => ({
  primary: 'bg-primary-600 text-white hover:bg-primary-700',
  secondary: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  ghost: 'text-slate-700 hover:bg-slate-100'
}[props.variant] || 'bg-primary-600 text-white hover:bg-primary-700'))

const handleClick = (event) => {
  if (isDisabled.value) {
    event.preventDefault()
    return
  }
  emit('click', event)
}
</script>
