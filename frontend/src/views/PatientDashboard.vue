<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { useAuth } from '../composables/useAuth'

const { user } = useAuth()
const appointments = ref([])
const doctors = ref([])
const availableSlots = ref([])
const medicalRecords = ref([])
const loading = ref(true)
const bookingSuccess = ref(false)
const bookingError = ref('')

const selectedDoctor = ref('')
const selectedSlot = ref('')

const fetchAppointments = async () => {
  try {
    if (user.value.id) {
      const res = await axios.get('/api/v1/appointments/patient/' + user.value.id)
      appointments.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

const fetchDoctors = async () => {
  try {
    const res = await axios.get('/api/v1/doctors')
    doctors.value = res.data || []
  } catch (error) {
    console.error(error)
  }
}

const fetchSlots = async () => {
  if (!selectedDoctor.value) {
    availableSlots.value = []
    return
  }
  try {
    const res = await axios.get(`/api/v1/appointment-slots/available?doctorId=${selectedDoctor.value}`)
    availableSlots.value = res.data || []
  } catch (error) {
    console.error(error)
  }
}

const fetchMedicalRecords = async () => {
  try {
    if (user.value.id) {
      const res = await axios.get('/api/v1/medical-records/patient/' + user.value.id)
      medicalRecords.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

const bookAppointment = async () => {
  bookingError.value = ''
  bookingSuccess.value = false
  
  if (!selectedSlot.value) {
    bookingError.value = 'Vui lòng chọn khung giờ khám'
    return
  }
  
  try {
    // API uses request params: ?patientId=...&slotId=...
    await axios.post(`/api/v1/appointments?patientId=${user.value.id}&slotId=${selectedSlot.value}`)
    bookingSuccess.value = true
    selectedDoctor.value = ''
    selectedSlot.value = ''
    availableSlots.value = []
    await fetchAppointments()
  } catch (error) {
    bookingError.value = error.response?.data?.message || 'Không thể đặt lịch khám. Khung giờ này có thể đã được người khác đặt.'
  }
}

onMounted(async () => {
  await fetchDoctors()
  await fetchAppointments()
  await fetchMedicalRecords()
  loading.value = false
})
</script>

<template>
  <div class="container mx-auto px-4 py-8 max-w-6xl">
    <div class="flex justify-between items-end mb-6">
      <h2 class="text-2xl font-bold text-gray-800 border-l-4 border-blue-500 pl-3">Bảng điều khiển Bệnh nhân</h2>
    </div>
    
    <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-100 mb-8 flex items-center justify-between">
      <div>
        <h3 class="text-gray-500 text-sm font-semibold uppercase tracking-wider mb-1">Xin chào,</h3>
        <p class="text-2xl font-bold text-gray-800">{{ user.fullName }}</p>
      </div>
      <div class="w-16 h-16 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-3xl">
        <i class="fas fa-user"></i>
      </div>
    </div>
    
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      
      <!-- Đặt lịch khám mới -->
      <div class="lg:col-span-1">
        <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-100 sticky top-24">
          <h3 class="text-lg font-bold text-gray-800 mb-4 pb-2 border-b"><i class="fas fa-calendar-plus text-blue-500 mr-2"></i> Đặt lịch khám mới</h3>
          
          <div v-if="bookingSuccess" class="bg-green-100 border-l-4 border-green-500 text-green-700 p-4 mb-4" role="alert">
            <p class="font-bold">Thành công!</p>
            <p>Lịch khám của bạn đã được đặt. Vui lòng đến đúng giờ.</p>
          </div>
          
          <div v-if="bookingError" class="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 mb-4" role="alert">
            <p>{{ bookingError }}</p>
          </div>
          
          <div class="mb-4">
            <label class="block text-gray-700 text-sm font-bold mb-2">1. Chọn Bác sĩ</label>
            <select v-model="selectedDoctor" @change="fetchSlots" class="w-full border border-gray-300 rounded px-3 py-2 outline-none focus:border-blue-500">
              <option value="">-- Chọn bác sĩ --</option>
              <option v-for="doc in doctors" :key="doc.id" :value="doc.id">
                {{ doc.fullName }} ({{ doc.specialty?.name || 'Chuyên khoa' }})
              </option>
            </select>
          </div>
          
          <div class="mb-6">
            <label class="block text-gray-700 text-sm font-bold mb-2">2. Chọn khung giờ (Hôm nay)</label>
            <div v-if="!selectedDoctor" class="text-sm text-gray-500 italic">
              Vui lòng chọn bác sĩ để xem khung giờ
            </div>
            <div v-else-if="availableSlots.length === 0" class="text-sm text-red-500 italic">
              Không có khung giờ trống nào trong hôm nay
            </div>
            <div v-else class="grid grid-cols-2 gap-2">
              <label v-for="slot in availableSlots" :key="slot.id" 
                     class="border rounded text-center py-2 cursor-pointer transition"
                     :class="selectedSlot === slot.id ? 'bg-blue-500 text-white border-blue-500' : 'bg-white text-gray-700 hover:bg-gray-50'">
                <input type="radio" :value="slot.id" v-model="selectedSlot" class="hidden">
                {{ slot.startTime.substring(0, 5) }} - {{ slot.endTime.substring(0, 5) }}
              </label>
            </div>
          </div>
          
          <button @click="bookAppointment" :disabled="!selectedSlot" 
                  class="w-full font-bold py-3 px-4 rounded transition"
                  :class="selectedSlot ? 'bg-blue-500 text-white hover:bg-blue-600' : 'bg-gray-300 text-gray-500 cursor-not-allowed'">
            Xác nhận đặt lịch
          </button>
        </div>
      </div>
      
      <!-- Danh sách lịch khám -->
      <div class="lg:col-span-2">
        <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-100">
          <h3 class="text-lg font-bold text-gray-800 mb-4 pb-2 border-b"><i class="fas fa-history text-blue-500 mr-2"></i> Lịch sử & Lịch khám sắp tới</h3>
          
          <div v-if="loading" class="text-center py-8">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500 mx-auto"></div>
          </div>
          
          <div v-else-if="appointments.length === 0" class="text-center py-10 bg-gray-50 rounded-lg">
            <i class="fas fa-calendar-times text-4xl text-gray-300 mb-3"></i>
            <p class="text-gray-500">Bạn chưa có lịch hẹn khám nào.</p>
          </div>
          
          <div v-else class="space-y-4">
            <div v-for="apt in appointments" :key="apt.id" class="border border-gray-200 rounded-lg p-4 flex flex-col sm:flex-row justify-between items-start sm:items-center hover:shadow-md transition">
              <div class="mb-3 sm:mb-0">
                <div class="flex items-center gap-2 mb-1">
                  <span class="font-bold text-lg text-gray-800">
                    {{ apt.appointmentDate }}
                  </span>
                  <span class="px-2 py-1 text-xs font-bold rounded"
                        :class="{
                          'bg-yellow-100 text-yellow-800': apt.status === 'BOOKED',
                          'bg-blue-100 text-blue-800': apt.status === 'CONFIRMED',
                          'bg-green-100 text-green-800': apt.status === 'COMPLETED',
                          'bg-red-100 text-red-800': apt.status === 'CANCELLED'
                        }">
                    {{ apt.status }}
                  </span>
                </div>
                <div class="text-gray-600">
                  <i class="fas fa-user-md text-gray-400 w-5"></i> Bác sĩ: <strong>{{ apt.doctorName }}</strong>
                </div>
                <div class="text-gray-600">
                  <i class="fas fa-clock text-gray-400 w-5"></i> Thời gian: <strong>{{ apt.startTime }} - {{ apt.endTime }}</strong>
                </div>
              </div>
              
              <div class="flex gap-2 w-full sm:w-auto">
                <button v-if="apt.status === 'BOOKED' || apt.status === 'CONFIRMED'" 
                        class="px-4 py-2 text-sm border border-red-500 text-red-500 rounded hover:bg-red-50 w-full sm:w-auto transition">
                  Hủy lịch
                </button>
                <button v-if="apt.status === 'COMPLETED'" 
                        class="px-4 py-2 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 w-full sm:w-auto transition shadow-sm">
                  Xem bệnh án
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- Hồ sơ bệnh án -->
      <div class="lg:col-span-3 mt-4">
        <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-100">
          <h3 class="text-lg font-bold text-gray-800 mb-4 pb-2 border-b"><i class="fas fa-notes-medical text-green-500 mr-2"></i> Hồ sơ bệnh án</h3>
          
          <div v-if="loading" class="text-center py-8">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500 mx-auto"></div>
          </div>
          
          <div v-else-if="medicalRecords.length === 0" class="text-center py-10 bg-gray-50 rounded-lg">
            <i class="fas fa-folder-open text-4xl text-gray-300 mb-3"></i>
            <p class="text-gray-500">Bạn chưa có hồ sơ bệnh án nào.</p>
          </div>
          
          <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div v-for="record in medicalRecords" :key="record.id" class="border border-gray-200 rounded-lg p-4 hover:shadow-md transition bg-gray-50">
              <div class="flex justify-between items-start mb-2">
                <h4 class="font-bold text-lg text-gray-800">{{ record.diagnosis || 'Chẩn đoán' }}</h4>
                <span class="text-sm text-gray-500">{{ record.recordDate || record.createdAt }}</span>
              </div>
              <div class="text-sm text-gray-600 space-y-1">
                <p><span class="font-semibold">Bác sĩ:</span> {{ record.doctorName }}</p>
                <p><span class="font-semibold">Triệu chứng:</span> {{ record.symptoms }}</p>
                <p><span class="font-semibold">Đơn thuốc:</span> {{ record.prescription || 'Không có' }}</p>
                <p><span class="font-semibold">Ghi chú:</span> {{ record.notes || 'Không có' }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>
