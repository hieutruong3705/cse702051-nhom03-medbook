<template>
  <PublicLayout>
    <section class="mx-auto flex min-h-[70vh] max-w-2xl flex-col items-center justify-center px-4 text-center">
      <p class="text-sm font-semibold uppercase tracking-wide text-primary-700">404</p>
      <h1 class="mt-2 text-3xl font-bold text-slate-950">Không tìm thấy trang</h1>
      <p class="mt-3 text-slate-600">Đường dẫn có thể đã thay đổi hoặc không còn tồn tại.</p>
      <RouterLink class="mt-6 rounded-md bg-primary-600 px-4 py-2 text-sm font-semibold text-white hover:bg-primary-700" to="/">
        Về trang chủ
      </RouterLink>
    </section>
  </PublicLayout>
</template>

<script setup>
import PublicLayout from '@/layouts/PublicLayout.vue'
</script>
