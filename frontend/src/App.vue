<script setup>
import { useRouter } from 'vue-router'
import { useAuth } from './composables/useAuth'

const router = useRouter()
const { isAuthenticated, user, logout, dashboardRoute } = useAuth()

const handleLogout = () => {
  logout()
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex flex-col font-sans">
    <!-- Header -->
    <header class="bg-white shadow-sm sticky top-0 z-50">
      <div class="container mx-auto px-4 lg:px-8 py-3 flex justify-between items-center">
        <div class="flex items-center gap-6">
          <!-- Logo -->
          <router-link to="/" class="flex items-center gap-2">
            <div class="w-10 h-10 bg-cyan-500 text-white rounded-lg flex items-center justify-center font-bold text-xl shadow-md">
              <i class="fas fa-heartbeat"></i>
            </div>
            <span class="text-2xl font-bold text-gray-800 tracking-tight">Med<span class="text-cyan-500">Book</span></span>
          </router-link>
          
          <!-- Menu (Desktop) -->
          <nav class="hidden md:flex gap-6 ml-10">
            <router-link to="/" class="text-gray-600 hover:text-cyan-500 font-medium transition">Trang chủ</router-link>
            <a href="#" class="text-gray-600 hover:text-cyan-500 font-medium transition">Chuyên khoa</a>
            <a href="#" class="text-gray-600 hover:text-cyan-500 font-medium transition">Bác sĩ</a>
            <a href="#" class="text-gray-600 hover:text-cyan-500 font-medium transition">Cơ sở y tế</a>
          </nav>
        </div>

        <div>
          <template v-if="isAuthenticated">
            <div class="flex items-center gap-4">
              <div class="hidden sm:block text-right">
                <div class="text-sm text-gray-500">Xin chào,</div>
                <div class="font-bold text-gray-800">{{ user?.fullName || 'Người dùng' }}</div>
              </div>
              <router-link :to="dashboardRoute" class="w-10 h-10 rounded-full bg-cyan-100 text-cyan-600 flex items-center justify-center hover:bg-cyan-200 transition">
                <i class="fas fa-user"></i>
              </router-link>
              <button @click="handleLogout" class="text-red-500 hover:text-red-600 font-medium transition flex items-center gap-1">
                <i class="fas fa-sign-out-alt"></i> <span class="hidden sm:inline">Đăng xuất</span>
              </button>
            </div>
          </template>
          <template v-else>
            <div class="flex gap-3">
              <router-link to="/login" class="text-gray-600 hover:text-cyan-600 font-medium px-4 py-2 transition">
                Đăng nhập
              </router-link>
              <router-link to="/login" class="bg-cyan-500 hover:bg-cyan-600 text-white font-medium px-5 py-2 rounded-lg shadow-md shadow-cyan-200 transition flex items-center gap-2">
                <i class="fas fa-calendar-check"></i> Đặt khám
              </router-link>
            </div>
          </template>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-grow">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
    
    <!-- Footer -->
    <footer class="bg-gray-800 text-white py-12">
      <div class="container mx-auto px-4 lg:px-8 grid grid-cols-1 md:grid-cols-4 gap-8">
        <div>
          <div class="flex items-center gap-2 mb-4">
            <div class="w-8 h-8 bg-cyan-500 text-white rounded flex items-center justify-center font-bold">
              <i class="fas fa-heartbeat"></i>
            </div>
            <span class="text-xl font-bold">MedBook</span>
          </div>
          <p class="text-gray-400 text-sm">Nền tảng y tế chăm sóc sức khỏe toàn diện, đặt lịch khám nhanh chóng và tiện lợi.</p>
        </div>
        <div>
          <h4 class="font-bold mb-4 text-gray-200">Dịch vụ</h4>
          <ul class="space-y-2 text-sm text-gray-400">
            <li><a href="#" class="hover:text-cyan-400">Đặt khám bác sĩ</a></li>
            <li><a href="#" class="hover:text-cyan-400">Khám từ xa</a></li>
            <li><a href="#" class="hover:text-cyan-400">Xét nghiệm tại nhà</a></li>
          </ul>
        </div>
        <div>
          <h4 class="font-bold mb-4 text-gray-200">Hỗ trợ</h4>
          <ul class="space-y-2 text-sm text-gray-400">
            <li><a href="#" class="hover:text-cyan-400">Câu hỏi thường gặp</a></li>
            <li><a href="#" class="hover:text-cyan-400">Điều khoản sử dụng</a></li>
            <li><a href="#" class="hover:text-cyan-400">Chính sách bảo mật</a></li>
          </ul>
        </div>
        <div>
          <h4 class="font-bold mb-4 text-gray-200">Liên hệ</h4>
          <ul class="space-y-2 text-sm text-gray-400">
            <li><i class="fas fa-map-marker-alt mr-2 text-cyan-500"></i> Hà Nội, Việt Nam</li>
            <li><i class="fas fa-phone mr-2 text-cyan-500"></i> 1900 1234</li>
            <li><i class="fas fa-envelope mr-2 text-cyan-500"></i> support@medbook.vn</li>
          </ul>
        </div>
      </div>
      <div class="container mx-auto px-4 lg:px-8 mt-8 pt-8 border-t border-gray-700 text-center text-sm text-gray-500">
        &copy; 2026 MedBook. Bản quyền thuộc Nhóm 03.
      </div>
    </footer>
  </div>
</template>

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
