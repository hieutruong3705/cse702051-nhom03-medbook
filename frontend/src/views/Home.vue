<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const specialties = ref([])
const doctors = ref([])
const loading = ref(true)

const services = [
  { id: 1, title: 'Khám Chuyên khoa', icon: 'fas fa-hospital', color: 'text-cyan-500' },
  { id: 2, title: 'Khám từ xa', icon: 'fas fa-mobile-alt', color: 'text-blue-500', badge: 'fas fa-plus', badgeColor: 'text-yellow-500' },
  { id: 3, title: 'Khám tổng quát', icon: 'fas fa-clipboard-list', color: 'text-blue-400', badge: 'fas fa-plus', badgeColor: 'text-yellow-500' },
  { id: 4, title: 'Xét nghiệm y học', icon: 'fas fa-vial', color: 'text-blue-500', badge: 'fas fa-plus', badgeColor: 'text-yellow-500' }
]

onMounted(async () => {
  try {
    const [specRes, docRes] = await Promise.all([
      axios.get('/api/v1/specialties').catch(() => ({ data: [] })),
      axios.get('/api/v1/doctors').catch(() => ({ data: [] }))
    ])
    specialties.value = specRes.data || []
    doctors.value = docRes.data || []
  } catch (err) {
    console.error("Failed to load data", err)
  } finally {
    loading.value = false
  }
})

const navigateToBooking = () => {
  router.push('/login')
}
</script>

<template>
  <div class="bg-white min-h-screen font-sans text-gray-800 pb-20">
    <!-- Hero Banner -->
    <div class="relative w-full h-[500px] overflow-hidden bg-[#f5f5f5]">
      <!-- Background Image Mock -->
      <div class="absolute inset-0 bg-cover bg-center" style="background-image: url('https://bookingcare.vn/assets/icon/bookingcare-cover-4.jpg'); background-color: #f7d800;">
        <div class="absolute inset-0 bg-black/30"></div>
      </div>
      
      <div class="relative z-10 h-full flex flex-col justify-center items-center text-center px-4">
        <h1 class="text-3xl md:text-5xl font-bold text-white mb-2 drop-shadow-lg text-shadow">
          NỀN TẢNG Y TẾ<br>
          <span class="font-normal text-2xl md:text-4xl mt-2 block">CHĂM SÓC SỨC KHỎE TOÀN DIỆN</span>
        </h1>
        
        <!-- Search Bar -->
        <div class="w-full max-w-2xl mt-8 bg-white/95 rounded-full shadow-lg flex items-center p-1 border-2 border-transparent focus-within:border-cyan-400 transition">
          <div class="pl-4 text-gray-600">
            <i class="fas fa-search"></i>
          </div>
          <input type="text" placeholder="Tìm bệnh viện, bác sĩ, chuyên khoa..." 
                 class="w-full py-3 px-4 outline-none text-gray-800 bg-transparent text-base">
        </div>
      </div>
    </div>

    <!-- AI Product Section (Matches Screenshot) -->
    <div class="bg-[#9bd7f1] w-full py-12">
      <div class="container mx-auto px-4 max-w-6xl">
        <h2 class="text-2xl font-bold text-gray-900 mb-6">Sản phẩm hỗ trợ bởi AI</h2>
        
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-2 gap-6">
          <!-- AI Assistant Card -->
          <div @click="navigateToBooking" class="bg-white rounded-2xl p-6 shadow-sm hover:shadow-md transition cursor-pointer flex items-center gap-4 border border-gray-100">
            <div class="w-16 h-16 rounded-full bg-blue-50 flex-shrink-0 flex items-center justify-center relative border border-blue-100">
              <i class="far fa-calendar-alt text-2xl text-cyan-500"></i>
              <div class="absolute -bottom-1 -right-1 w-6 h-6 bg-yellow-400 rounded-full flex items-center justify-center text-white text-xs border-2 border-white">
                <i class="fas fa-robot"></i>
              </div>
            </div>
            <div>
              <h3 class="font-bold text-lg text-gray-900 mb-1">Trợ lý Đi khám</h3>
              <p class="text-gray-600 text-sm leading-snug">Tìm kiếm thông tin bác sĩ, nơi khám và đặt lịch khám</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Comprehensive Services Section (Matches Screenshot) -->
    <div class="bg-white w-full py-12">
      <div class="container mx-auto px-4 max-w-6xl">
        <h2 class="text-2xl font-bold text-gray-900 mb-6">Dịch vụ toàn diện</h2>
        
        <div class="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
          <div v-for="service in services" :key="service.id" 
               @click="navigateToBooking"
               class="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm hover:shadow-md transition cursor-pointer flex items-center gap-5 group relative overflow-hidden">
            <!-- Background cross watermark like bookingcare -->
            <div class="absolute right-4 top-1/2 -translate-y-1/2 text-gray-50 opacity-50 text-8xl z-0 pointer-events-none">
              <i class="fas fa-plus"></i>
            </div>
            
            <div class="w-14 h-14 bg-gray-50 rounded-xl flex-shrink-0 flex items-center justify-center relative z-10 shadow-sm border border-gray-100">
              <i :class="[service.icon, service.color, 'text-3xl']"></i>
              <i v-if="service.badge" :class="[service.badge, service.badgeColor, 'absolute -bottom-1 -right-1 text-xs bg-white rounded-full p-0.5']"></i>
            </div>
            <h3 class="font-semibold text-xl text-gray-800 z-10 group-hover:text-cyan-600 transition">{{ service.title }}</h3>
          </div>
        </div>
      </div>
    </div>

    <!-- Chuyên khoa -->
    <div class="bg-gray-100 w-full py-12">
      <div class="container mx-auto px-4 max-w-6xl">
        <div class="flex justify-between items-center mb-6">
          <h2 class="text-2xl font-bold text-gray-900">Chuyên khoa phổ biến</h2>
          <button class="bg-gray-200 hover:bg-gray-300 text-gray-800 px-4 py-2 rounded font-medium text-sm transition uppercase">Xem thêm</button>
        </div>
        
        <div class="flex gap-6 overflow-x-auto pb-4 snap-x">
          <div v-for="spec in specialties" :key="spec.id" 
               @click="navigateToBooking"
               class="flex-shrink-0 w-64 snap-start cursor-pointer group">
            <div class="w-full h-40 bg-white rounded-lg shadow-sm border border-gray-200 mb-3 overflow-hidden">
              <img :src="'https://picsum.photos/seed/' + spec.id + '/300/200'" class="w-full h-full object-cover group-hover:scale-105 transition duration-300" />
            </div>
            <h3 class="font-semibold text-lg text-gray-800 group-hover:text-cyan-600 transition">{{ spec.name }}</h3>
          </div>
          <div v-if="specialties.length === 0 && !loading" class="text-gray-500">Chưa có dữ liệu chuyên khoa</div>
        </div>
      </div>
    </div>

    <!-- Bác sĩ nổi bật -->
    <div class="bg-white w-full py-12">
      <div class="container mx-auto px-4 max-w-6xl">
        <div class="flex justify-between items-center mb-6">
          <h2 class="text-2xl font-bold text-gray-900">Bác sĩ nổi bật tuần qua</h2>
          <button class="bg-gray-200 hover:bg-gray-300 text-gray-800 px-4 py-2 rounded font-medium text-sm transition uppercase">Xem thêm</button>
        </div>
        
        <div class="flex gap-6 overflow-x-auto pb-4 snap-x">
          <div v-for="doc in doctors" :key="doc.id" 
               @click="navigateToBooking"
               class="flex-shrink-0 w-64 snap-start cursor-pointer group border border-gray-100 rounded-xl p-4 shadow-sm hover:shadow-md transition text-center">
            <div class="w-32 h-32 mx-auto rounded-full bg-gray-200 mb-4 overflow-hidden border-2 border-cyan-100">
              <img :src="'https://ui-avatars.com/api/?name=' + doc.fullName + '&background=0D8ABC&color=fff&size=150'" class="w-full h-full object-cover group-hover:scale-110 transition duration-300" />
            </div>
            <h3 class="font-bold text-lg text-gray-800 mb-1 group-hover:text-cyan-600 transition">Bác sĩ {{ doc.fullName.replace('Bac si ', '') }}</h3>
            <p class="text-gray-500 text-sm">{{ doc.specialty?.name || 'Chuyên khoa Nội' }}</p>
          </div>
          <div v-if="doctors.length === 0 && !loading" class="text-gray-500">Chưa có dữ liệu bác sĩ</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.text-shadow {
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
}
::-webkit-scrollbar {
  height: 8px;
}
::-webkit-scrollbar-track {
  background: #f1f1f1; 
  border-radius: 4px;
}
::-webkit-scrollbar-thumb {
  background: #ccc; 
  border-radius: 4px;
}
::-webkit-scrollbar-thumb:hover {
  background: #999; 
}
</style>
