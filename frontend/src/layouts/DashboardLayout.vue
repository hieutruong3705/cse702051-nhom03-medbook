<template>
  <div class="min-h-screen bg-slate-100">
    <aside class="fixed inset-y-0 left-0 z-40 hidden w-64 border-r border-slate-200 bg-white lg:block">
      <div class="flex h-16 items-center gap-2 border-b border-slate-200 px-5 text-lg font-bold">
        <span class="inline-flex h-9 w-9 items-center justify-center rounded-md bg-primary-600 text-white">
          <i class="fa-solid fa-heart-pulse" aria-hidden="true"></i>
        </span>
        MedBook
      </div>
      <nav class="space-y-1 p-3" :aria-label="title">
        <RouterLink
          v-for="item in items"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-primary-50 hover:text-primary-700"
          active-class="bg-primary-50 text-primary-700"
        >
          <i :class="item.icon" class="w-4" aria-hidden="true"></i>
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="lg:pl-64">
      <header class="sticky top-0 z-30 border-b border-slate-200 bg-white">
        <div class="flex h-16 items-center justify-between gap-4 px-4 sm:px-6">
          <button type="button" class="rounded-md p-2 text-slate-700 lg:hidden" aria-label="Mở menu" :aria-expanded="mobileOpen" @click="mobileOpen = !mobileOpen">
            <i class="fa-solid fa-bars" aria-hidden="true"></i>
          </button>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm text-slate-500">{{ eyebrow }}</p>
            <p class="truncate font-semibold text-slate-950">{{ title }}</p>
          </div>
          <div class="flex items-center gap-3">
            <NotificationBell v-if="notificationsPath" :to="notificationsPath" />
            <!-- Màn hình hẹp: hồ sơ, đổi mật khẩu, đăng xuất nằm trong menu di động để thanh trên không tràn ngang -->
            <div class="hidden md:block">
              <UserMenu variant="dashboard" />
            </div>
          </div>
        </div>
        <nav v-if="mobileOpen" class="border-t border-slate-200 bg-white p-3 lg:hidden" :aria-label="`${title} di động`">
          <RouterLink
            v-for="item in items"
            :key="item.to"
            :to="item.to"
            class="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-primary-50"
            @click="mobileOpen = false"
          >
            <i :class="item.icon" class="w-4" aria-hidden="true"></i>
            {{ item.label }}
          </RouterLink>
          <div class="mt-3 border-t border-slate-200 pt-3 md:hidden">
            <UserMenu variant="dashboard" stacked @navigate="mobileOpen = false" />
          </div>
        </nav>
      </header>

      <div class="px-4 py-6 sm:px-6 lg:px-8">
        <slot />
      </div>

      <footer class="border-t border-slate-200 px-4 py-4 text-xs text-slate-600 sm:px-6 lg:px-8">
        <p>{{ PROJECT.name }} · {{ PROJECT.topic }} · {{ PROJECT.group }} · Lớp học phần {{ PROJECT.classSection }}</p>
        <p class="mt-1" data-test="data-notice">{{ PROJECT.dataNotice }}.</p>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import UserMenu from '@/components/auth/UserMenu.vue'
import NotificationBell from '@/components/notifications/NotificationBell.vue'
import { PROJECT } from '@/config/project'

// `profilePath` được giữ để tương thích với các layout khu vực; hồ sơ/đổi mật khẩu/đăng xuất nằm
// trong UserMenu (hồ sơ tài khoản dùng chung cho mọi vai trò ở /account/profile).
defineProps({
  title: { type: String, required: true },
  eyebrow: { type: String, default: 'MedBook' },
  items: { type: Array, default: () => [] },
  profilePath: { type: String, default: '/account/profile' },
  // Đường dẫn trang thông báo; để trống (Admin) thì không hiện chuông.
  notificationsPath: { type: String, default: '' }
})

const mobileOpen = ref(false)
</script>
