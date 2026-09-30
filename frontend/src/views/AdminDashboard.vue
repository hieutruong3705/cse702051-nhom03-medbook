<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '../composables/useAuth'

const { user } = useAuth()
const stats = ref({
  users: 0,
  appointments: 0,
  revenue: 0
})
const loading = ref(false)
const error = ref(null)

const fetchStats = async () => {
  loading.value = true
  try {
    const response = await fetch('/api/v1/data-metrics/dashboard', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })
    if (!response.ok) throw new Error('Failed to fetch statistics')
    const data = await response.json()
    stats.value = {
      users: data.totalUsers || 0,
      appointments: data.totalAppointments || 0,
      revenue: data.totalRevenue || 0
    }
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<template>
  <div class="max-w-7xl mx-auto p-6">
    <div class="mb-8">
      <h2 class="text-3xl font-bold text-gray-800">Admin Dashboard</h2>
      <p class="text-gray-500 mt-1">Overview of system metrics. Welcome, {{ user.fullName || user.username }}!</p>
    </div>

    <div v-if="loading" class="text-gray-500 mb-6">
      Loading statistics...
    </div>
    <div v-else-if="error" class="text-red-500 mb-6 bg-red-50 p-4 rounded-lg">
      {{ error }}
    </div>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
      <!-- Users Card -->
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex items-center space-x-4">
        <div class="p-4 bg-indigo-50 text-indigo-600 rounded-lg">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
          </svg>
        </div>
        <div>
          <p class="text-sm font-medium text-gray-500">Total Users</p>
          <p class="text-3xl font-bold text-gray-800">{{ stats.users }}</p>
        </div>
      </div>

      <!-- Appointments Card -->
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex items-center space-x-4">
        <div class="p-4 bg-blue-50 text-blue-600 rounded-lg">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
        </div>
        <div>
          <p class="text-sm font-medium text-gray-500">Appointments</p>
          <p class="text-3xl font-bold text-gray-800">{{ stats.appointments }}</p>
        </div>
      </div>

      <!-- Revenue Card -->
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex items-center space-x-4">
        <div class="p-4 bg-green-50 text-green-600 rounded-lg">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div>
          <p class="text-sm font-medium text-gray-500">Total Revenue</p>
          <p class="text-3xl font-bold text-gray-800">${{ stats.revenue.toLocaleString() }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
