<template>
  <DashboardLayout title="Quản trị" eyebrow="Quản trị viên" :items="items" profile-path="/admin/profile">
    <slot />
  </DashboardLayout>
</template>

<script setup>
import DashboardLayout from './DashboardLayout.vue'

const items = [
  { to: '/admin/dashboard', label: 'Tổng quan', icon: 'fa-solid fa-chart-pie' },
  { to: '/admin/users', label: 'Tài khoản', icon: 'fa-solid fa-users' },
  { to: '/admin/doctors', label: 'Bác sĩ', icon: 'fa-solid fa-user-doctor' },
  { to: '/admin/catalog', label: 'Danh mục', icon: 'fa-solid fa-layer-group' },
  { to: '/admin/appointments', label: 'Lịch hẹn', icon: 'fa-regular fa-calendar-check' },
  { to: '/admin/invoices', label: 'Hóa đơn', icon: 'fa-solid fa-file-invoice-dollar' },
  { to: '/admin/reports', label: 'Báo cáo', icon: 'fa-solid fa-chart-column' },
  { to: '/admin/audit-logs', label: 'Nhật ký hệ thống', icon: 'fa-solid fa-shield-halved' }
]
</script>
