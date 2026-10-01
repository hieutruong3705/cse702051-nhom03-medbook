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
import AdminLayout from '@/layouts/AdminLayout.vue'
import PageHeader from '@/components/ui/PageHeader.vue'

const cards = [
  { label: 'Tài khoản', value: '--', icon: 'fa-solid fa-users' },
  { label: 'Bác sĩ', value: '--', icon: 'fa-solid fa-user-doctor' },
  { label: 'Lịch hẹn hôm nay', value: '--', icon: 'fa-regular fa-calendar-check' },
  { label: 'Hóa đơn chưa thu', value: '--', icon: 'fa-solid fa-file-invoice-dollar' }
]

const quickLinks = [
  { to: '/admin/users', label: 'Quản lý tài khoản', icon: 'fa-solid fa-users' },
  { to: '/admin/catalog', label: 'Quản lý danh mục', icon: 'fa-solid fa-layer-group' },
  { to: '/admin/reports', label: 'Xem báo cáo', icon: 'fa-solid fa-chart-column' },
  { to: '/admin/audit-logs', label: 'Tra audit log', icon: 'fa-solid fa-shield-halved' }
]
</script>
