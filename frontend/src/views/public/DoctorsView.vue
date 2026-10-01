<template>
  <PublicLayout>
    <PageHeader title="Bác sĩ" description="Tìm bác sĩ theo tên hoặc chuyên khoa." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div class="mb-6 grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_220px_auto]">
        <BaseInput id="doctor-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Tên bác sĩ" />
        <BaseInput id="doctor-specialty" v-model="filters.specialtyId" label="Mã chuyên khoa" />
        <div class="flex items-end">
          <BaseButton class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass" @click="loadDoctors">Tìm</BaseButton>
        </div>
      </div>

      <DataTable :columns="columns" :rows="doctors" :loading="loading" :error="error" empty-text="Chưa có bác sĩ phù hợp">
        <template #cell-fullName="{ row }">
          <div class="font-semibold text-slate-950">{{ row.fullName || row.name }}</div>
          <div class="text-xs text-slate-500">{{ row.specialty?.name || 'Chuyên khoa' }}</div>
        </template>
        <template #cell-action="{ row }">
          <RouterLink class="text-sm font-semibold text-primary-700 hover:text-primary-900" :to="`/doctors/${row.id}`">Chi tiết</RouterLink>
        </template>
      </DataTable>
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import DataTable from '@/components/ui/DataTable.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { doctorsApi } from '@/api/doctors'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const filters = reactive({ keyword: '', specialtyId: '' })
const doctors = ref([])
const loading = ref(false)
const error = ref('')
const columns = [
  { key: 'fullName', label: 'Bác sĩ' },
  { key: 'licenseNumber', label: 'Mã hành nghề' },
  { key: 'action', label: '' }
]

const loadDoctors = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorsApi.list({ ...filters })
    doctors.value = Array.isArray(response?.content) ? response.content : response || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadDoctors)
</script>
