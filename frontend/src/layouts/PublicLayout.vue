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

    <footer class="bg-slate-950 text-slate-300">
      <div class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div class="grid gap-10 md:grid-cols-[1.5fr_1fr_1fr]">
          <div>
            <RouterLink to="/" class="inline-flex items-center gap-2 text-xl font-bold text-white">
              <span class="inline-flex h-10 w-10 items-center justify-center rounded-md bg-primary-600 text-white">
                <i class="fa-solid fa-heart-pulse" aria-hidden="true"></i>
              </span>
              MedBook
            </RouterLink>
            <p class="mt-4 max-w-sm text-sm leading-6">
              Đặt lịch khám trực tuyến, theo dõi lịch hẹn, kết quả khám, đơn thuốc và hóa đơn của bạn ở cùng một nơi.
            </p>
            <RouterLink
              v-if="canBook"
              to="/patient/booking"
              class="mt-6 inline-flex items-center rounded-md bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700"
              data-test="footer-booking"
            >
              <i class="fa-solid fa-calendar-plus mr-2" aria-hidden="true"></i>Đặt lịch khám
            </RouterLink>
          </div>

          <nav aria-labelledby="footer-explore">
            <h2 id="footer-explore" class="text-sm font-semibold uppercase tracking-wide text-white">Khám phá</h2>
            <ul class="mt-4 space-y-3 text-sm">
              <li v-for="item in publicNav" :key="item.to">
                <RouterLink :to="item.to" class="hover:text-white hover:underline">{{ item.label }}</RouterLink>
              </li>
            </ul>
          </nav>

          <nav aria-labelledby="footer-account">
            <h2 id="footer-account" class="text-sm font-semibold uppercase tracking-wide text-white">Tài khoản</h2>
            <ul class="mt-4 space-y-3 text-sm">
              <li v-for="item in accountNav" :key="item.to">
                <RouterLink :to="item.to" class="hover:text-white hover:underline">{{ item.label }}</RouterLink>
              </li>
            </ul>
          </nav>
        </div>

        <!-- Bắt buộc theo yêu cầu học phần: chân trang trang công khai ghi tên đề tài, tên nhóm, tên lớp học phần và dòng dữ liệu mô phỏng. -->
        <div
          class="mt-10 flex flex-col gap-3 border-t border-white/10 pt-6 text-sm text-slate-400 md:flex-row md:items-start md:justify-between md:gap-8"
        >
          <div>
            <p data-test="copyright">© {{ year }} {{ PROJECT.name }}</p>
            <p class="mt-1" data-test="project-info">
              Đề tài: {{ PROJECT.topic }} · {{ PROJECT.group }} · Lớp học phần {{ PROJECT.course }} ({{ PROJECT.classSection }})
            </p>
          </div>
          <p class="md:shrink-0 md:text-right" data-test="data-notice">{{ PROJECT.dataNotice }}.</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import UserMenu from '@/components/auth/UserMenu.vue'
import { PROJECT } from '@/config/project'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const open = ref(false)
const year = new Date().getFullYear()
const publicNav = [
  { to: '/', label: 'Trang chủ' },
  { to: '/specialties', label: 'Chuyên khoa' },
  { to: '/doctors', label: 'Bác sĩ' },
  { to: '/services', label: 'Dịch vụ' }
]

// Chỉ bệnh nhân đặt được lịch; khách bấm vào thì đăng nhập rồi quay lại trang đặt lịch (route guard).
const canBook = computed(() => !auth.isAuthenticated || auth.hasRole('PATIENT'))
const accountNav = computed(() =>
  auth.isAuthenticated
    ? [
        { to: auth.dashboardPath, label: 'Bảng điều khiển' },
        { to: '/account/profile', label: 'Hồ sơ tài khoản' },
        { to: '/account/change-password', label: 'Đổi mật khẩu' }
      ]
    : [
        { to: '/login', label: 'Đăng nhập' },
        { to: '/register', label: 'Đăng ký' },
        { to: '/forgot-password', label: 'Quên mật khẩu' }
      ]
)
</script>
