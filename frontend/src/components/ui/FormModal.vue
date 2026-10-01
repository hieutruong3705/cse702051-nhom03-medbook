<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-[70]">
      <div class="absolute inset-0 bg-slate-900/50" @click="close"></div>
      <div class="flex min-h-full items-center justify-center p-4">
        <section
          ref="panel"
          class="relative w-full max-w-lg rounded-md bg-white shadow-xl"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
          @keydown.esc.prevent="close"
        >
          <header class="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
            <div>
              <h2 :id="titleId" class="text-lg font-semibold text-slate-950">{{ title }}</h2>
              <p v-if="description" class="mt-1 text-sm text-slate-600">{{ description }}</p>
            </div>
            <button type="button" class="rounded p-1 text-slate-500 hover:bg-slate-100" aria-label="Đóng" @click="close">
              <i class="fa-solid fa-xmark" aria-hidden="true"></i>
            </button>
          </header>
          <div class="px-5 py-4">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="flex justify-end gap-2 border-t border-slate-200 px-5 py-4">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, required: true },
  description: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'close'])
const panel = ref(null)
const titleId = `modal-title-${Math.random().toString(36).slice(2)}`
let previousFocus = null

const close = () => {
  emit('update:modelValue', false)
  emit('close')
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement
      await nextTick()
      panel.value?.focus()
    } else if (previousFocus?.focus) {
      previousFocus.focus()
    }
  }
)
</script>
