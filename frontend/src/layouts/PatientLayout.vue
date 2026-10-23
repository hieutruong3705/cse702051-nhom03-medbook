<template>
  <DashboardLayout title="Khu bệnh nhân" eyebrow="Bệnh nhân" :items="items" profile-path="/patient/profile" notifications-path="/patient/notifications">
    <slot />
  </DashboardLayout>
</template>

<script setup>
import DashboardLayout from './DashboardLayout.vue'

const items = [
  { to: '/patient/booking', label: 'Đặt lịch khám', icon: 'fa-solid fa-calendar-plus' },
  { to: '/patient/dashboard', label: 'Tổng quan', icon: 'fa-solid fa-chart-line' },
  { to: '/patient/appointments', label: 'Lịch hẹn', icon: 'fa-regular fa-calendar-check' },
  { to: '/patient/records', label: 'Bệnh án', icon: 'fa-regular fa-folder-open' },
  { to: '/patient/invoices', label: 'Hóa đơn', icon: 'fa-solid fa-file-invoice-dollar' },
  { to: '/patient/notifications', label: 'Thông báo', icon: 'fa-regular fa-bell' },
  { to: '/patient/profile', label: 'Hồ sơ', icon: 'fa-regular fa-user' }
]
</script>
