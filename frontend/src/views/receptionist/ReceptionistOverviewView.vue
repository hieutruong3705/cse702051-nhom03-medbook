<template>
  <ReceptionistLayout>
    <div class="space-y-6">
      <PageHeader
        title="Tổng quan lễ tân"
        eyebrow="Quầy lễ tân"
        description="Theo dõi lịch hẹn trong ngày và các hóa đơn cần thu tại quầy."
      />

      <section v-if="loading" class="rounded-md border border-slate-200 bg-white p-6">
        <LoadingSpinner label="Đang tải tổng quan lễ tân" />
      </section>

      <section v-else-if="error" class="rounded-md border border-red-200 bg-red-50 p-4" role="alert">
        <p class="text-sm font-medium text-red-700">{{ error }}</p>
        <BaseButton class="mt-3" variant="secondary" icon="fa-solid fa-rotate-right" @click="load">Thử lại</BaseButton>
      </section>

      <template v-else>
        <section aria-labelledby="appointment-summary-title">
          <h2 id="appointment-summary-title" class="sr-only">Số lịch hẹn hôm nay</h2>
          <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <article v-for="card in appointmentCards" :key="card.label" class="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
              <div class="flex items-center justify-between gap-4">
                <div>
                  <p class="text-sm text-slate-500">{{ card.label }}</p>
                  <p class="mt-2 text-3xl font-bold text-slate-950">{{ card.value }}</p>
                </div>
                <span class="flex h-12 w-12 items-center justify-center rounded-md bg-primary-50 text-primary-700">
                  <i :class="card.icon" aria-hidden="true"></i>
                </span>
              </div>
            </article>
          </div>
        </section>

        <section aria-labelledby="invoice-summary-title" class="grid gap-4 lg:grid-cols-2">
          <h2 id="invoice-summary-title" class="sr-only">Tổng quan hóa đơn</h2>
          <article class="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
            <p class="text-sm text-slate-500">Hóa đơn chưa thu</p>
            <p class="mt-2 text-3xl font-bold text-slate-950">{{ overview.unpaidInvoiceCount }}</p>
            <p class="mt-1 text-sm font-semibold text-slate-700">{{ formatCurrency(overview.unpaidInvoiceAmount) }}</p>
          </article>
          <article class="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
            <p class="text-sm text-slate-500">Đã thu hôm nay</p>
            <p class="mt-2 text-3xl font-bold text-slate-950">{{ overview.collectedInvoiceCount }}</p>
            <p class="mt-1 text-sm font-semibold text-slate-700">{{ formatCurrency(overview.collectedAmount) }}</p>
          </article>
        </section>

        <section class="rounded-md border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="booked-title">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 id="booked-title" class="text-lg font-semibold text-slate-950">Lịch hẹn sắp tới hôm nay</h2>
            <RouterLink to="/receptionist/appointments" class="text-sm font-semibold text-primary-700 hover:text-primary-800">
              Xem tất cả
            </RouterLink>
          </div>

          <EmptyState
            v-if="!upcomingAppointments.length"
            class="mt-4"
            icon="fa-regular fa-calendar-check"
            title="Hôm nay chưa có lịch hẹn"
            message="Các lịch đã đặt trong ngày sẽ hiển thị tại đây."
          />
          <ul v-else class="mt-4 divide-y divide-slate-200">
            <li v-for="item in upcomingAppointments" :key="item.id" class="flex flex-wrap items-center justify-between gap-3 py-3">
              <div>
                <p class="font-semibold text-slate-950">{{ formatTime(item.startTime) }} - {{ formatTime(item.endTime) }}</p>
                <p class="mt-1 text-sm text-slate-600">
                  {{ item.patientName }} · {{ item.doctorName }}
                </p>
              </div>
              <span class="text-xs font-semibold uppercase tracking-wide text-slate-500">{{ item.patientCode }}</span>
            </li>
          </ul>
        </section>

        <section class="grid gap-3 md:grid-cols-3" aria-label="Lối tắt lễ tân">
          <RouterLink v-for="item in quickLinks" :key="item.to" :to="item.to" class="rounded-md border border-slate-200 bg-white p-4 text-sm font-semibold text-slate-700 shadow-sm hover:border-primary-200 hover:bg-primary-50 hover:text-primary-700">
            <i :class="item.icon" class="mr-2" aria-hidden="true"></i>
            {{ item.label }}
          </RouterLink>
        </section>
      </template>
    </div>
  </ReceptionistLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { BaseButton, EmptyState, LoadingSpinner, PageHeader } from '@/components/ui'
import ReceptionistLayout from '@/layouts/ReceptionistLayout.vue'
import { receptionApi } from '@/api/reception'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatTime } from '@/utils/formatters'

const emptyOverview = {
  date: '',
  bookedCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  cancelledCount: 0,
  unpaidInvoiceCount: 0,
  unpaidInvoiceAmount: 0,
  collectedInvoiceCount: 0,
  collectedAmount: 0
}

const overview = ref({ ...emptyOverview })
const upcomingAppointments = ref([])
const loading = ref(true)
const error = ref('')

const today = () => {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

const appointmentCards = computed(() => [
  { label: 'Đã đặt', value: overview.value.bookedCount, icon: 'fa-regular fa-calendar-check' },
  { label: 'Đang khám', value: overview.value.inProgressCount, icon: 'fa-solid fa-stethoscope' },
  { label: 'Hoàn thành', value: overview.value.completedCount, icon: 'fa-solid fa-circle-check' },
  { label: 'Đã hủy', value: overview.value.cancelledCount, icon: 'fa-solid fa-ban' }
])

const quickLinks = [
  { to: '/receptionist/booking', label: 'Đặt lịch hộ', icon: 'fa-solid fa-calendar-plus' },
  { to: '/receptionist/patients', label: 'Bệnh nhân', icon: 'fa-solid fa-user-injured' },
  { to: '/receptionist/invoices', label: 'Hóa đơn', icon: 'fa-solid fa-file-invoice-dollar' }
]

async function load() {
  loading.value = true
  error.value = ''
  const date = today()
  try {
    const [overviewResponse, appointmentsResponse] = await Promise.all([
      receptionApi.overview({ date }),
      receptionApi.appointments.list({ from: date, to: date, status: 'BOOKED', page: 0, size: 10 })
    ])
    overview.value = { ...emptyOverview, ...overviewResponse }
    upcomingAppointments.value = appointmentsResponse?.content ?? []
  } catch (err) {
    overview.value = { ...emptyOverview }
    upcomingAppointments.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
