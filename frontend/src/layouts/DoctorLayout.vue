<template>
  <DashboardLayout title="Khu bác sĩ" eyebrow="Bác sĩ" :items="items" profile-path="/doctor/profile" notifications-path="/doctor/notifications">
    <slot />
  </DashboardLayout>
</template>

<script setup>
import DashboardLayout from './DashboardLayout.vue'

const items = [
  { to: '/doctor/dashboard', label: 'Tổng quan', icon: 'fa-solid fa-chart-line' },
  { to: '/doctor/schedules', label: 'Lịch làm việc', icon: 'fa-regular fa-calendar-days' },
  { to: '/doctor/appointments', label: 'Lịch khám', icon: 'fa-solid fa-stethoscope' },
  { to: '/doctor/patients', label: 'Bệnh nhân', icon: 'fa-solid fa-user-injured' },
  { to: '/doctor/notifications', label: 'Thông báo', icon: 'fa-regular fa-bell' },
  { to: '/doctor/profile', label: 'Hồ sơ', icon: 'fa-regular fa-user' }
]
</script>
