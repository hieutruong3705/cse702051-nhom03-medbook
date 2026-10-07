<template>
  <div class="flex min-h-screen flex-col bg-slate-50">
    <header class="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div class="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3 sm:px-6 lg:px-8">
        <RouterLink to="/" class="flex items-center gap-2 text-xl font-bold text-slate-950">
          <span class="inline-flex h-10 w-10 items-center justify-center rounded-md bg-primary-600 text-white">
            <i class="fa-solid fa-heart-pulse" aria-hidden="true"></i>
          </span>
          Med<span class="text-primary-600">Book</span>
        </RouterLink>

        <button class="rounded-md p-2 text-slate-700 md:hidden" type="button" aria-label="Mở menu" @click="open = !open">
          <i class="fa-solid fa-bars" aria-hidden="true"></i>
        </button>

        <nav class="hidden items-center gap-6 md:flex" aria-label="Chính">
          <RouterLink v-for="item in publicNav" :key="item.to" :to="item.to" class="text-sm font-medium text-slate-700 hover:text-primary-700">
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="hidden items-center gap-2 md:flex">
          <UserMenu variant="public" />
        </div>
      </div>

      <nav v-if="open" class="border-t border-slate-200 bg-white px-4 py-3 md:hidden" aria-label="Di động">
        <RouterLink
          v-for="item in publicNav"
          :key="item.to"
          :to="item.to"
          class="block rounded px-2 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          @click="open = false"
        >
          {{ item.label }}
        </RouterLink>
        <div class="mt-3 border-t border-slate-200 pt-3">
          <UserMenu variant="public" stacked @navigate="open = false" />
        </div>
      </nav>
    </header>

    <main class="flex-1">
      <slot />
    </main>

    <footer class="border-t border-slate-200 bg-white">
      <div class="mx-auto grid max-w-7xl gap-6 px-4 py-8 text-sm text-slate-600 sm:px-6 md:grid-cols-3 lg:px-8">
        <div>
          <p class="font-semibold text-slate-900">{{ PROJECT.name }}</p>
          <p class="mt-2">Đề tài: {{ PROJECT.topic }}.</p>
        </div>
        <div>
          <p class="font-semibold text-slate-900">{{ PROJECT.group }}</p>
          <p class="mt-2">Lớp học phần {{ PROJECT.course }} ({{ PROJECT.classSection }})</p>
        </div>
        <div>
          <p class="font-semibold text-slate-900">Lưu ý</p>
          <p class="mt-2" data-test="data-notice">{{ PROJECT.dataNotice }}.</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { PROJECT } from '@/config/project'
import { ref } from 'vue'
import UserMenu from '@/components/auth/UserMenu.vue'

const open = ref(false)
const publicNav = [
  { to: '/', label: 'Trang chủ' },
  { to: '/specialties', label: 'Chuyên khoa' },
  { to: '/doctors', label: 'Bác sĩ' },
  { to: '/services', label: 'Dịch vụ' }
]
</script>
