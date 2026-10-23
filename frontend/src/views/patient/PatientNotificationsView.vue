<template>
  <PatientLayout>
    <PageHeader title="Thông báo" eyebrow="Bệnh nhân" description="Thông báo đặt lịch, đổi lịch, hủy lịch và nhắc lịch khám của bạn." />
    <div class="mt-4">
      <NotificationsPanel />
    </div>
  </PatientLayout>
</template>

<script setup>
import NotificationsPanel from '@/components/notifications/NotificationsPanel.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PatientLayout from '@/layouts/PatientLayout.vue'
</script>
