<template>
  <DoctorLayout>
    <PageHeader eyebrow="Khu bác sĩ" :title="`Xin chào, ${auth.displayName}`" description="Lịch khám của bạn trong hôm nay." />

    <section class="mt-6" aria-labelledby="today-title">
      <h2 id="today-title" class="text-lg font-semibold text-slate-950">Lịch khám hôm nay</h2>

      <div v-if="loading" class="mt-4 rounded-md border border-slate-200 bg-white p-4">
        <LoadingSpinner label="Đang tải lịch khám" />
      </div>
      <p v-else-if="error" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <EmptyState v-else-if="!appointments.length" class="mt-4" icon="fa-regular fa-calendar-check" title="Hôm nay chưa có lịch khám" message="Khi bệnh nhân đặt lịch vào các khung giờ của bạn, lịch sẽ hiện ở đây." />
      <ul v-else class="mt-4 space-y-3" data-test="today-list">
        <li v-for="item in appointments" :key="item.id" class="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 bg-white p-4">
          <div>
            <p class="font-semibold text-slate-950">{{ formatTime(item.startTime) }}-{{ formatTime(item.endTime) }} · {{ item.patientName }}</p>
            <p class="mt-1 text-sm text-slate-600">
              <span v-if="item.serviceName">{{ item.serviceName }}</span>
              <span v-if="item.notes"> · {{ item.notes }}</span>
            </p>
          </div>
          <StatusBadge :value="item.status" :label="appointmentStatus[item.status]" />
        </li>
      </ul>
    </section>
  </DoctorLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import LoadingSpinner from '@/components/ui/LoadingSpinner.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import { appointmentsApi } from '@/api/appointments'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatTime } from '@/utils/formatters'
import { appointmentStatus } from '@/utils/statusLabels'

const auth = useAuthStore()
const appointments = ref([])
const loading = ref(true)
const error = ref('')

const today = () => new Date().toISOString().slice(0, 10)

onMounted(async () => {
  try {
    // Backend lấy bác sĩ từ JWT; không truyền doctorId.
    const page = await appointmentsApi.mine({ from: today(), to: today(), order: 'asc', size: 50 })
    appointments.value = (page.content || []).filter((item) => item.status !== 'CANCELLED')
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>
