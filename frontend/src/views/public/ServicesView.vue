<template>
  <PublicLayout>
    <PageHeader title="Dịch vụ khám" description="Bảng dịch vụ dùng khi đặt lịch và lập hóa đơn." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <DataTable :columns="columns" :rows="services" :loading="loading" :error="error" empty-text="Chưa có dịch vụ">
        <template #cell-price="{ value }">
          {{ formatCurrency(value) }}
        </template>
        <template #cell-durationMinutes="{ value }">
          {{ value }} phút
        </template>
      </DataTable>
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import DataTable from '@/components/ui/DataTable.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { catalogApi } from '@/api/catalog'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency } from '@/utils/formatters'

const services = ref([])
const loading = ref(false)
const error = ref('')
const columns = [
  { key: 'name', label: 'Dịch vụ' },
  { key: 'durationMinutes', label: 'Thời lượng' },
  { key: 'price', label: 'Giá' }
]

onMounted(async () => {
  loading.value = true
  try {
    const response = await catalogApi.services.list({ status: 'ACTIVE' })
    services.value = Array.isArray(response?.content) ? response.content : response || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>
