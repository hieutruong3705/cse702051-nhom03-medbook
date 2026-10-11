<template>
  <DashboardLayout title="Quầy lễ tân" eyebrow="Lễ tân" :items="items">
    <slot />
  </DashboardLayout>
</template>

<script setup>
import DashboardLayout from './DashboardLayout.vue'

const items = [
  { to: '/receptionist/dashboard', label: 'Tổng quan', icon: 'fa-solid fa-chart-line' },
  { to: '/receptionist/appointments', label: 'Lịch hẹn', icon: 'fa-regular fa-calendar-check' },
  { to: '/receptionist/booking', label: 'Đặt lịch hộ', icon: 'fa-solid fa-calendar-plus' },
  { to: '/receptionist/patients', label: 'Bệnh nhân', icon: 'fa-solid fa-user-injured' },
  { to: '/receptionist/invoices', label: 'Hóa đơn', icon: 'fa-solid fa-file-invoice-dollar' },
  { to: '/account/profile', label: 'Hồ sơ', icon: 'fa-regular fa-user' }
]
</script>
