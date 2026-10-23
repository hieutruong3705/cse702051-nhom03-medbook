<template>
  <section class="rounded-md border border-slate-200 bg-white" aria-live="polite">
    <header class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
      <label class="flex items-center gap-2 text-sm text-slate-700">
        <input id="notifications-unread-only" v-model="unreadOnly" type="checkbox" class="h-4 w-4 rounded border-slate-300" @change="reload" />
        Chỉ hiện chưa đọc
      </label>
      <BaseButton variant="secondary" icon="fa-solid fa-check-double" :disabled="!hasUnread" :loading="markingAll" @click="markAll">
        Đánh dấu tất cả đã đọc
      </BaseButton>
    </header>

    <div v-if="loading" class="p-6">
      <LoadingSpinner label="Đang tải thông báo" />
    </div>
    <div v-else-if="error" class="p-6 text-center" role="alert">
      <p class="text-sm text-red-700">{{ error }}</p>
      <BaseButton class="mt-3" variant="secondary" icon="fa-solid fa-rotate-right" @click="load">Thử lại</BaseButton>
    </div>
    <div v-else-if="!notifications.length" class="p-4">
      <EmptyState
        icon="fa-regular fa-bell"
        title="Chưa có thông báo"
        message="Thông báo đặt lịch, đổi lịch, hủy lịch và nhắc lịch khám sẽ xuất hiện tại đây."
      />
    </div>
    <ul v-else class="divide-y divide-slate-200">
      <li v-for="item in notifications" :key="item.id" class="flex flex-wrap items-start justify-between gap-3 p-4" :class="item.readAt ? '' : 'bg-primary-50/40'">
        <div class="min-w-0 flex-1">
          <p class="font-semibold text-slate-950">{{ item.title }}</p>
          <p class="mt-1 text-sm text-slate-700">{{ item.message }}</p>
          <p class="mt-2 text-xs text-slate-500">{{ formatDateTime(item.createdAt) }}</p>
        </div>
        <div class="flex items-center gap-2">
          <span v-if="item.readAt" class="text-xs text-slate-500">Đã đọc</span>
          <BaseButton v-else variant="ghost" :aria-label="`Đánh dấu đã đọc: ${item.title}`" @click="markRead(item)">Đánh dấu đã đọc</BaseButton>
        </div>
      </li>
    </ul>
    <Pagination v-if="!loading && !error && notifications.length" :page="page" :total-pages="totalPages" @update:page="changePage" />
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { BaseButton, EmptyState, LoadingSpinner, Pagination } from '@/components/ui'
import { notificationsApi } from '@/api/notifications'
import { useNotificationCount } from '@/composables/useNotificationCount'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'

// Danh sách thông báo của người đang đăng nhập (bệnh nhân hoặc bác sĩ). Thông báo chưa đọc có readAt rỗng.
const notifications = ref([])
const loading = ref(true)
const error = ref('')
const unreadOnly = ref(false)
const markingAll = ref(false)
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })
const { refresh: refreshCount } = useNotificationCount()

const hasUnread = computed(() => notifications.value.some((item) => !item.readAt))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (unreadOnly.value) params.unreadOnly = true
    const response = await notificationsApi.mine(params)
    notifications.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    notifications.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return load()
}

function changePage(next) {
  page.value = next
  load()
}

async function markRead(item) {
  try {
    const updated = await notificationsApi.markRead(item.id)
    item.readAt = updated?.readAt || new Date().toISOString()
    refreshCount()
  } catch (err) {
    error.value = apiErrorMessage(err)
  }
}

async function markAll() {
  if (markingAll.value) return
  markingAll.value = true
  try {
    await notificationsApi.markAllRead()
    await load()
    refreshCount()
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    markingAll.value = false
  }
}

onMounted(load)
</script>
