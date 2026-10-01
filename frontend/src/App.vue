<template>
  <div class="min-h-screen bg-slate-50 text-ink-900">
    <a class="skip-link" href="#main-content">Bỏ qua tới nội dung</a>
    <main id="main-content" tabindex="-1">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    <ToastHost />
  </div>
</template>

<script setup>
import ToastHost from '@/components/ui/ToastHost.vue'
</script>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
