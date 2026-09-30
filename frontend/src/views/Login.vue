<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../composables/useAuth'
import axios from 'axios'

const router = useRouter()
const { login, dashboardRoute } = useAuth()

const username = ref('')
const password = ref('')
const errorMsg = ref('')
const loading = ref(false)

const handleLogin = async () => {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await axios.post('/api/v1/auth/login', {
      usernameOrEmail: username.value,
      password: password.value
    })
    
    const token = res.data.accessToken || res.data.token
    const user = res.data.user || res.data
    
    login(token, user)
    router.push(dashboardRoute.value)
  } catch (err) {
    errorMsg.value = err.response?.data?.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
    <div class="sm:mx-auto sm:w-full sm:max-w-md">
      <h2 class="mt-6 text-center text-3xl font-extrabold text-gray-900">
        Đăng nhập MedBook
      </h2>
      <p class="mt-2 text-center text-sm text-gray-600">
        Nền tảng y tế số toàn diện
      </p>
    </div>

    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
      <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10 border-t-4 border-cyan-500">
        <form class="space-y-6" @submit.prevent="handleLogin">
          <div v-if="errorMsg" class="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
            <p class="text-red-700 text-sm">{{ errorMsg }}</p>
          </div>

          <div>
            <label for="username" class="block text-sm font-medium text-gray-700">Tên đăng nhập hoặc Email</label>
            <div class="mt-1 relative rounded-md shadow-sm">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <i class="fas fa-user text-gray-400"></i>
              </div>
              <input id="username" v-model="username" type="text" required class="focus:ring-cyan-500 focus:border-cyan-500 block w-full pl-10 sm:text-sm border-gray-300 rounded-md py-2 border outline-none" placeholder="Nhập tên đăng nhập">
            </div>
          </div>

          <div>
            <label for="password" class="block text-sm font-medium text-gray-700">Mật khẩu</label>
            <div class="mt-1 relative rounded-md shadow-sm">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <i class="fas fa-lock text-gray-400"></i>
              </div>
              <input id="password" v-model="password" type="password" required class="focus:ring-cyan-500 focus:border-cyan-500 block w-full pl-10 sm:text-sm border-gray-300 rounded-md py-2 border outline-none" placeholder="Nhập mật khẩu">
            </div>
          </div>

          <div>
            <button type="submit" :disabled="loading" class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-cyan-600 hover:bg-cyan-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-cyan-500 transition-colors">
              <span v-if="loading"><i class="fas fa-spinner fa-spin mr-2"></i> Đang xử lý...</span>
              <span v-else>Đăng nhập</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
