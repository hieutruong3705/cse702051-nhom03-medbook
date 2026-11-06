<template>
  <PublicLayout>
    <PageHeader title="Bác sĩ" description="Tìm bác sĩ theo tên hoặc chuyên khoa rồi đặt lịch khám." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <form ref="filterForm" class="mb-6 grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_240px_auto]" @submit.prevent="reload">
        <BaseInput id="doctor-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Tên bác sĩ hoặc chuyên khoa" />
        <BaseSelect id="doctor-specialty" v-model="filters.specialtyId" label="Chuyên khoa" placeholder="Tất cả chuyên khoa" :options="specialtyOptions" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass">Tìm</BaseButton>
        </div>
      </form>

      <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
      <div v-else-if="loading" data-test="doctors-skeleton">
        <p class="sr-only" role="status">Đang tải danh sách bác sĩ…</p>
        <ul class="space-y-4" aria-hidden="true">
          <li v-for="n in 3" :key="n" class="grid gap-5 rounded-md border border-slate-200 bg-white p-5 md:grid-cols-2">
            <div class="flex gap-4">
              <span class="h-20 w-20 shrink-0 animate-pulse rounded-full bg-slate-200"></span>
              <Skeleton class="flex-1 pt-2" :lines="4" :widths="['55%', '35%', '90%', '75%']" />
            </div>
            <div class="space-y-3 md:border-l md:border-slate-200 md:pl-5">
              <div class="flex gap-2">
                <span v-for="d in 5" :key="d" class="h-12 w-16 animate-pulse rounded-md bg-slate-200"></span>
              </div>
              <div class="flex flex-wrap gap-2">
                <span v-for="s in 6" :key="s" class="h-9 w-28 animate-pulse rounded bg-slate-200"></span>
              </div>
            </div>
          </li>
        </ul>
      </div>
      <EmptyState
        v-else-if="!doctors.length"
        illustration="search"
        title="Chưa có bác sĩ phù hợp"
        message="Thử bỏ bớt từ khóa hoặc chọn &quot;Tất cả chuyên khoa&quot; rồi bấm Tìm."
      />
      <ul v-else class="space-y-4" aria-label="Danh sách bác sĩ">
        <li v-for="(doctor, index) in doctors" :key="doctor.id" v-reveal="index"><DoctorCard :doctor="doctor" /></li>
      </ul>
      <Pagination class="mt-4" :page="page" :total-pages="totalPages" @update:page="changePage" />
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BaseButton, BaseInput, BaseSelect, EmptyState, PageHeader, Pagination, Skeleton } from '@/components/ui'
import DoctorCard from '@/components/booking/DoctorCard.vue'
import { reveal as vReveal } from '@/utils/motion'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { prefersReducedMotion } from '@/utils/motion'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams } from '@/utils/params'

// Mỗi bác sĩ là một thẻ: hồ sơ ngắn bên trái, lịch khám chọn được ngay bên phải (không lộ số giấy phép hay điện thoại).

// Trang chủ dẫn sang đây kèm `?keyword=` (ô tìm kiếm) hoặc `?specialtyId=` (thẻ chuyên khoa).
const route = useRoute()
const queryText = (value) => String((Array.isArray(value) ? value[0] : value) ?? '').trim()
const initialSpecialtyId = queryText(route.query.specialtyId)

const filters = reactive({
  keyword: queryText(route.query.keyword).slice(0, 100),
  specialtyId: /^\d+$/.test(initialSpecialtyId) ? initialSpecialtyId : ''
})
const doctors = ref([])
const specialtyOptions = ref([])
const loading = ref(false)
const error = ref('')
// Mỗi thẻ có dải 7 ngày và giờ khám (một lời gọi API giờ trống mỗi thẻ), nên 8 thẻ mỗi trang để trang không quá dài
// trên điện thoại và không gọi quá nhiều API cùng lúc.
const PAGE_SIZE = 8
const filterForm = ref(null)
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: PAGE_SIZE })

async function loadDoctors() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorsApi.list(cleanParams({ ...filters, page: page.value, size: size.value }))
    doctors.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    doctors.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return loadDoctors()
}

function changePage(next) {
  page.value = next
  loadDoctors()
  // Đưa người dùng về đầu danh sách thay vì để họ ở cuối trang sau khi bấm "Sau"
  filterForm.value?.scrollIntoView?.({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' })
}

onMounted(async () => {
  loadDoctors()
  try {
    const response = await catalogApi.specialties.list({ size: 100 })
    specialtyOptions.value = (response?.content ?? []).map((item) => ({ label: item.name, value: String(item.id) }))
  } catch {
    specialtyOptions.value = []
  }
})
</script>
