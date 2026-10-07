<template>
  <DoctorLayout>
    <PageHeader title="Thông báo" eyebrow="Bác sĩ" description="Thông báo khi bệnh nhân đặt, đổi hoặc hủy lịch khám với bạn, và nhắc lịch sắp tới." />
    <div class="mt-4">
      <NotificationsPanel />
    </div>
  </DoctorLayout>
</template>

<script setup>
import NotificationsPanel from '@/components/notifications/NotificationsPanel.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
</script>
