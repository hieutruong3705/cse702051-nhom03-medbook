<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace('/patient/dashboard')
})
</script>

<template>
  <section class="mx-auto max-w-xl px-4 py-16 text-center">
    <h1 class="text-2xl font-bold text-slate-950">Khu bệnh nhân</h1>
    <p class="mt-3 text-slate-600">Đang chuyển tới bảng điều khiển bệnh nhân hiện tại.</p>
    <RouterLink to="/patient/dashboard" class="mt-6 inline-block font-semibold text-primary-700 hover:text-primary-900">
      Tới bảng điều khiển
    </RouterLink>
  </section>
</template>
