<template>
  <div class="fixed right-4 top-4 z-[80] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-3" aria-live="polite">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="rounded-md border bg-white p-4 shadow-lg"
      :class="toneClass(toast.type)"
      role="status"
    >
      <div class="flex items-start gap-3">
        <i class="mt-0.5" :class="iconClass(toast.type)" aria-hidden="true"></i>
        <div class="min-w-0 flex-1">
          <p class="font-semibold text-slate-900">{{ toast.title }}</p>
          <p v-if="toast.message" class="mt-1 text-sm text-slate-700">{{ toast.message }}</p>
        </div>
        <button
          type="button"
          class="rounded p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-primary-600"
          aria-label="Đóng thông báo"
          @click="remove(toast.id)"
        >
          <i class="fa-solid fa-xmark" aria-hidden="true"></i>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useToast } from '@/composables/useToast'

const { toasts, remove } = useToast()

const toneClass = (type) => ({
  success: 'border-emerald-200',
  error: 'border-red-200',
  warning: 'border-amber-200',
  info: 'border-primary-100'
}[type] || 'border-primary-100')

const iconClass = (type) => ({
  success: 'fa-solid fa-circle-check text-emerald-600',
  error: 'fa-solid fa-triangle-exclamation text-red-600',
  warning: 'fa-solid fa-circle-exclamation text-amber-600',
  info: 'fa-solid fa-circle-info text-primary-600'
}[type] || 'fa-solid fa-circle-info text-primary-600')
</script>
