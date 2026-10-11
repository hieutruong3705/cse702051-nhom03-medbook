<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Tổng quan quản trị" description="Theo dõi nhanh dữ liệu vận hành của hệ thống." />

      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <article v-for="card in cards" :key="card.label" class="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
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
      <p v-if="loadFailed" class="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800" role="alert">
        Chưa tải được một số số liệu. Tải lại trang để thử lại.
      </p>

      <section class="rounded-md border border-slate-200 bg-white p-5 shadow-sm">
        <h2 class="text-lg font-semibold text-slate-950">Cần xử lý tiếp</h2>
        <div class="mt-4 grid gap-3 md:grid-cols-3">
          <RouterLink v-for="item in quickLinks" :key="item.to" :to="item.to" class="rounded-md border border-slate-200 p-4 text-sm font-semibold text-slate-700 hover:border-primary-200 hover:bg-primary-50 hover:text-primary-700">
            <i :class="item.icon" class="mr-2" aria-hidden="true"></i>
            {{ item.label }}
          </RouterLink>
        </div>
      </section>
    </div>
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { adminUsersApi } from '@/api/adminUsers'
import { appointmentsApi } from '@/api/appointments'
import { doctorsApi } from '@/api/doctors'
import { invoicesApi } from '@/api/invoices'

const isoToday = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

// Mỗi thẻ là tổng số dòng của một danh sách quản trị có sẵn (chỉ xin một dòng, đọc totalElements).
const cards = reactive([
  { label: 'Tài khoản', value: '--', icon: 'fa-solid fa-users', count: () => adminUsersApi.list({ size: 1 }) },
  { label: 'Bác sĩ', value: '--', icon: 'fa-solid fa-user-doctor', count: () => doctorsApi.listAdmin({ size: 1 }) },
  {
    label: 'Lịch hẹn hôm nay',
    value: '--',
    icon: 'fa-regular fa-calendar-check',
    count: () => appointmentsApi.listAdmin({ from: isoToday(), to: isoToday(), size: 1 })
  },
  {
    label: 'Hóa đơn chưa thu',
    value: '--',
    icon: 'fa-solid fa-file-invoice-dollar',
    count: () => invoicesApi.listAdmin({ status: 'UNPAID', size: 1 })
  }
])
const loadFailed = ref(false)

onMounted(async () => {
  const results = await Promise.allSettled(cards.map((card) => card.count()))
  results.forEach((result, index) => {
    const total = result.status === 'fulfilled' ? result.value?.totalElements : undefined
    if (typeof total === 'number') cards[index].value = new Intl.NumberFormat('vi-VN').format(total)
    else loadFailed.value = true
  })
})

const quickLinks = [
  { to: '/admin/users', label: 'Quản lý tài khoản', icon: 'fa-solid fa-users' },
  { to: '/admin/catalog', label: 'Quản lý danh mục', icon: 'fa-solid fa-layer-group' },
  { to: '/admin/reports', label: 'Xem báo cáo', icon: 'fa-solid fa-chart-column' },
  { to: '/admin/audit-logs', label: 'Tra nhật ký hệ thống', icon: 'fa-solid fa-shield-halved' }
]
</script>
