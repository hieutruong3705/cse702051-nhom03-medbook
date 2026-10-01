<template>
  <DashboardLayout title="Khu bác sĩ" eyebrow="DOCTOR" :items="items" profile-path="/doctor/profile">
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
  { to: '/doctor/profile', label: 'Hồ sơ', icon: 'fa-regular fa-user' }
]
</script>
