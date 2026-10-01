<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import PublicLayout from '@/layouts/PublicLayout.vue'
import heroImage from '@/assets/hero.png'

const router = useRouter()
const specialties = ref([])
const doctors = ref([])
const loading = ref(true)

const services = [
  { id: 1, title: 'Khám chuyên khoa', icon: 'fa-solid fa-hospital', tone: 'text-primary-600' },
  { id: 2, title: 'Khám tổng quát', icon: 'fa-solid fa-clipboard-list', tone: 'text-emerald-600' },
  { id: 3, title: 'Xét nghiệm', icon: 'fa-solid fa-vial', tone: 'text-indigo-600' },
  { id: 4, title: 'Theo dõi hồ sơ', icon: 'fa-solid fa-file-medical', tone: 'text-amber-600' }
]

onMounted(async () => {
  try {
    const [specRes, docRes] = await Promise.all([
      axios.get('/api/v1/specialties').catch(() => ({ data: [] })),
      axios.get('/api/v1/doctors').catch(() => ({ data: [] }))
    ])
    specialties.value = Array.isArray(specRes.data?.content) ? specRes.data.content : specRes.data || []
    doctors.value = Array.isArray(docRes.data?.content) ? docRes.data.content : docRes.data || []
  } finally {
    loading.value = false
  }
})

const navigateToBooking = () => {
  router.push('/login')
}
</script>

<template>
  <PublicLayout>
    <section class="relative overflow-hidden bg-slate-950 text-white">
      <img :src="heroImage" alt="" class="absolute inset-0 h-full w-full object-cover opacity-35" />
      <div class="relative mx-auto flex min-h-[520px] max-w-7xl flex-col justify-center px-4 py-16 sm:px-6 lg:px-8">
        <div class="max-w-3xl">
          <h1 class="text-4xl font-bold sm:text-5xl">MedBook</h1>
          <p class="mt-5 max-w-2xl text-lg text-slate-100">
            Đặt lịch khám, theo dõi hồ sơ và quản lý lần khám trong một hệ thống mô phỏng thống nhất.
          </p>
          <div class="mt-8 flex max-w-xl items-center rounded-md bg-white p-2 shadow-lg">
            <i class="fa-solid fa-magnifying-glass px-3 text-slate-500" aria-hidden="true"></i>
            <input class="min-w-0 flex-1 px-2 py-3 text-slate-900 outline-none" type="search" placeholder="Tìm bác sĩ hoặc chuyên khoa" />
            <button class="rounded-md bg-primary-600 px-4 py-3 text-sm font-semibold text-white hover:bg-primary-700" type="button" @click="navigateToBooking">
              Đặt khám
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="bg-white py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-slate-950">Dịch vụ toàn diện</h2>
        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button
            v-for="service in services"
            :key="service.id"
            type="button"
            class="rounded-md border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
            @click="navigateToBooking"
          >
            <i :class="[service.icon, service.tone, 'text-3xl']" aria-hidden="true"></i>
            <h3 class="mt-4 text-lg font-semibold text-slate-950">{{ service.title }}</h3>
            <p class="mt-2 text-sm text-slate-600">Chọn dịch vụ, xem slot trống và hoàn tất đặt lịch sau khi đăng nhập.</p>
          </button>
        </div>
      </div>
    </section>

    <section class="bg-slate-100 py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between gap-4">
          <h2 class="text-2xl font-bold text-slate-950">Chuyên khoa phổ biến</h2>
          <RouterLink to="/specialties" class="text-sm font-semibold text-primary-700 hover:text-primary-900">Xem thêm</RouterLink>
        </div>
        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button
            v-for="spec in specialties"
            :key="spec.id"
            type="button"
            class="rounded-md border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:shadow-md"
            @click="navigateToBooking"
          >
            <div class="flex h-24 items-center justify-center rounded bg-primary-50 text-3xl text-primary-700">
              <i class="fa-solid fa-staff-snake" aria-hidden="true"></i>
            </div>
            <h3 class="mt-4 font-semibold text-slate-950">{{ spec.name }}</h3>
          </button>
          <p v-if="specialties.length === 0 && !loading" class="text-slate-600">Chưa có dữ liệu chuyên khoa.</p>
        </div>
      </div>
    </section>

    <section class="bg-white py-12">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between gap-4">
          <h2 class="text-2xl font-bold text-slate-950">Bác sĩ</h2>
          <RouterLink to="/doctors" class="text-sm font-semibold text-primary-700 hover:text-primary-900">Xem thêm</RouterLink>
        </div>
        <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button
            v-for="doctor in doctors"
            :key="doctor.id"
            type="button"
            class="rounded-md border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:shadow-md"
            @click="navigateToBooking"
          >
            <div class="flex h-20 w-20 items-center justify-center rounded-full bg-slate-100 text-2xl font-bold text-primary-700">
              {{ (doctor.fullName || doctor.name || 'BS').slice(0, 2).toUpperCase() }}
            </div>
            <h3 class="mt-4 font-semibold text-slate-950">{{ doctor.fullName || doctor.name }}</h3>
            <p class="mt-1 text-sm text-slate-600">{{ doctor.specialty?.name || 'Chuyên khoa' }}</p>
          </button>
          <p v-if="doctors.length === 0 && !loading" class="text-slate-600">Chưa có dữ liệu bác sĩ.</p>
        </div>
      </div>
    </section>
  </PublicLayout>
</template>
