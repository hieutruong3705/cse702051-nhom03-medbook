<template>
  <PatientLayout>
    <PageHeader eyebrow="Khu bệnh nhân" :title="`Xin chào, ${auth.displayName}`" description="Theo dõi lịch khám sắp tới và truy cập nhanh các chức năng chính.">
      <template #actions>
        <RouterLink to="/doctors" class="inline-flex min-h-10 items-center gap-2 rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700">
          <i class="fa-solid fa-calendar-plus" aria-hidden="true"></i>Tìm bác sĩ &amp; đặt lịch
        </RouterLink>
      </template>
    </PageHeader>

    <section class="mt-6" aria-labelledby="upcoming-title">
      <h2 id="upcoming-title" class="text-lg font-semibold text-slate-950">Lịch khám sắp tới</h2>

      <div v-if="loading" class="mt-4 rounded-md border border-slate-200 bg-white p-4">
        <LoadingSpinner label="Đang tải lịch khám" />
      </div>
      <p v-else-if="error" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <EmptyState v-else-if="!appointments.length" class="mt-4" icon="fa-regular fa-calendar" title="Chưa có lịch khám sắp tới" message="Chọn bác sĩ và khung giờ phù hợp để đặt lịch khám đầu tiên.">
        <RouterLink to="/doctors" class="text-sm font-semibold text-primary-700 hover:text-primary-900">Tìm bác sĩ</RouterLink>
      </EmptyState>
      <ul v-else class="mt-4 space-y-3" data-test="upcoming-list">
        <li v-for="item in appointments" :key="item.id" class="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-200 bg-white p-4">
          <div>
            <p class="font-semibold text-slate-950">{{ item.doctorName }}<span v-if="item.specialtyName" class="font-normal text-slate-600"> · {{ item.specialtyName }}</span></p>
            <p class="mt-1 text-sm text-slate-600">
              {{ formatDate(item.date) }} · {{ formatTime(item.startTime) }}-{{ formatTime(item.endTime) }}
              <span v-if="item.serviceName"> · {{ item.serviceName }}</span>
            </p>
          </div>
          <StatusBadge :value="item.status" :label="appointmentStatus[item.status]" />
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
import { appointmentsApi } from '@/api/appointments'
import { useAuthStore } from '@/stores/auth'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDate, formatTime } from '@/utils/formatters'
import { appointmentStatus } from '@/utils/statusLabels'

const auth = useAuthStore()
const appointments = ref([])
const loading = ref(true)
const error = ref('')

const today = () => new Date().toISOString().slice(0, 10)

onMounted(async () => {
  try {
    // Backend lấy bệnh nhân từ JWT; không truyền patientId.
    const page = await appointmentsApi.mine({ status: 'BOOKED', from: today(), order: 'asc', size: 5 })
    appointments.value = page.content || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>
