<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '../composables/useAuth'

const { user } = useAuth()
const appointments = ref([])
const loading = ref(false)
const error = ref(null)

const fetchAppointments = async () => {
  if (!user.value || !user.value.id) return
  loading.value = true
  try {
    const response = await fetch(`/api/v1/appointments/doctor/${user.value.id}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })
    if (!response.ok) throw new Error('Failed to fetch appointments')
    const data = await response.json()
    appointments.value = data
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

const updateStatus = async (appointmentId, newStatus) => {
  try {
    const response = await fetch(`/api/v1/appointments/${appointmentId}/status`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({ status: newStatus })
    })
    if (!response.ok) throw new Error('Failed to update status')
    // Update local state
    const index = appointments.value.findIndex(a => a.id === appointmentId)
    if (index !== -1) {
      appointments.value[index].status = newStatus
    }
  } catch (err) {
    alert(err.message)
  }
}

onMounted(() => {
  fetchAppointments()
})
</script>

<template>
  <div class="max-w-7xl mx-auto p-6">
    <div class="flex items-center justify-between mb-8">
      <div>
        <h2 class="text-3xl font-bold text-gray-800">Doctor Dashboard</h2>
        <p class="text-gray-500 mt-1">Welcome back, Dr. {{ user.fullName || user.username }}</p>
      </div>
    </div>

    <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
      <div class="p-6 border-b border-gray-100">
        <h3 class="text-lg font-semibold text-gray-800">Your Appointments</h3>
      </div>
      
      <div v-if="loading" class="p-8 text-center text-gray-500">
        Loading appointments...
      </div>
      <div v-else-if="error" class="p-8 text-center text-red-500">
        {{ error }}
      </div>
      <div v-else-if="appointments.length === 0" class="p-8 text-center text-gray-500">
        No appointments found.
      </div>
      
      <div v-else class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-gray-50 text-gray-600 text-sm uppercase tracking-wider">
              <th class="p-4 font-medium">Patient</th>
              <th class="p-4 font-medium">Date & Time</th>
              <th class="p-4 font-medium">Reason</th>
              <th class="p-4 font-medium">Status</th>
              <th class="p-4 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="apt in appointments" :key="apt.id" class="hover:bg-gray-50 transition-colors">
              <td class="p-4">
                <div class="font-medium text-gray-800">{{ apt.patientName }}</div>
              </td>
              <td class="p-4 text-gray-600">
                {{ new Date(apt.appointmentDate).toLocaleString() }}
              </td>
              <td class="p-4 text-gray-600 truncate max-w-xs" :title="apt.reason">
                {{ apt.reason }}
              </td>
              <td class="p-4">
                <span :class="{
                  'px-3 py-1 text-xs font-semibold rounded-full': true,
                  'bg-yellow-100 text-yellow-800': apt.status === 'BOOKED',
                  'bg-blue-100 text-blue-800': apt.status === 'CONFIRMED',
                  'bg-green-100 text-green-800': apt.status === 'COMPLETED',
                  'bg-red-100 text-red-800': apt.status === 'CANCELLED'
                }">
                  {{ apt.status }}
                </span>
              </td>
              <td class="p-4 text-right space-x-2">
                <button 
                  v-if="apt.status === 'BOOKED'"
                  @click="updateStatus(apt.id, 'CONFIRMED')"
                  class="px-3 py-1.5 text-sm bg-blue-500 hover:bg-blue-600 text-white rounded-md transition-colors"
                >
                  Confirm
                </button>
                <button 
                  v-if="apt.status === 'CONFIRMED'"
                  @click="updateStatus(apt.id, 'COMPLETED')"
                  class="px-3 py-1.5 text-sm bg-green-500 hover:bg-green-600 text-white rounded-md transition-colors"
                >
                  Complete
                </button>
                <button 
                  v-if="['BOOKED', 'CONFIRMED'].includes(apt.status)"
                  @click="updateStatus(apt.id, 'CANCELLED')"
                  class="px-3 py-1.5 text-sm bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-md transition-colors"
                >
                  Cancel
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
