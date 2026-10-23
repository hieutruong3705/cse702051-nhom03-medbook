<template>
  <RouterLink
    :to="to"
    class="relative inline-flex h-10 w-10 items-center justify-center rounded-md text-slate-700 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600"
    :aria-label="count > 0 ? `Thông báo, ${count} chưa đọc` : 'Thông báo'"
    data-test="notification-bell"
  >
    <i class="fa-regular fa-bell" aria-hidden="true"></i>
    <span
      v-if="count > 0"
      class="absolute -right-0.5 -top-0.5 min-w-[1.25rem] rounded-full bg-red-600 px-1 text-center text-xs font-bold leading-5 text-white"
      aria-hidden="true"
    >
      {{ count > 99 ? '99+' : count }}
    </span>
  </RouterLink>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useNotificationCount } from '@/composables/useNotificationCount'

// Chuông thông báo cho bệnh nhân và bác sĩ: hỏi số chưa đọc khi mở trang và mỗi phút một lần.
defineProps({
  to: { type: String, required: true }
})

const { count, refresh } = useNotificationCount()
let timer = null

onMounted(() => {
  refresh()
  timer = window.setInterval(refresh, 60000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>
