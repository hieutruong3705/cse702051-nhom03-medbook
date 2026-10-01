<template>
  <PatientLayout>
    <PageHeader title="Thông báo" eyebrow="Bệnh nhân" description="Theo dõi nhắc lịch, thay đổi lịch hẹn và các cập nhật liên quan đến hồ sơ của bạn." />

    <section class="rounded-md border border-slate-200 bg-white">
      <div v-if="loading" class="p-6">
        <LoadingSpinner label="Đang tải thông báo" />
      </div>
      <p v-else-if="error" class="m-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="status">
        {{ error }}
      </p>
      <EmptyState
        v-else-if="!notifications.length"
        icon="fa-regular fa-bell"
        title="Chưa có thông báo"
        message="Khi backend bật thông báo đặt lịch, hủy lịch hoặc nhắc khám, các nội dung đó sẽ xuất hiện tại đây."
      />
      <ul v-else class="divide-y divide-slate-200">
        <li v-for="item in notifications" :key="item.id" class="flex flex-wrap items-start justify-between gap-3 p-4">
          <div>
            <p class="font-semibold text-slate-950">{{ item.title || notificationTitle(item) }}</p>
            <p class="mt-1 text-sm text-slate-600">{{ item.message || item.content || '-' }}</p>
            <p class="mt-2 text-xs text-slate-500">{{ formatDateTime(item.createdAt || item.sentAt) }}</p>
          </div>
          <StatusBadge :value="item.read ? 'READ' : 'UNREAD'" :label="item.read ? 'Đã đọc' : 'Chưa đọc'" />
        </li>
      </ul>
    </section>
  </PatientLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import LoadingSpinner from '@/components/ui/LoadingSpinner.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import PatientLayout from '@/layouts/PatientLayout.vue'
import { notificationsApi } from '@/api/notifications'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'

const notifications = ref([])
const loading = ref(true)
const error = ref('')

function notificationTitle(item) {
  return item.type || item.actionCode || 'Thông báo'
}

onMounted(async () => {
  try {
    const response = await notificationsApi.mine({ page: 0, size: 20 })
    notifications.value = response.content ?? response.items ?? response
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>
