<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Danh mục" description="Quản lý chuyên khoa, dịch vụ và thuốc." />

      <div class="rounded-md border border-slate-200 bg-white p-2">
        <div class="grid gap-2 sm:grid-cols-3">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="rounded px-4 py-2 text-sm font-semibold"
            :class="activeTab === tab.key ? 'bg-primary-600 text-white' : 'text-slate-700 hover:bg-slate-100'"
            @click="selectTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <section class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_180px_auto]">
        <BaseInput id="catalog-keyword" v-model="filters.keyword" label="Từ khóa" />
        <BaseSelect id="catalog-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <div class="flex items-end">
          <BaseButton class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass" @click="loadCatalog">Lọc</BaseButton>
        </div>
      </section>

      <DataTable :columns="columns" :rows="items" :loading="loading" :error="error" empty-text="Chưa có dữ liệu danh mục">
        <template #cell-status="{ value }">
          <StatusBadge :value="value || 'ACTIVE'" :label="catalogStatus[value] || value || 'ACTIVE'" />
        </template>
        <template #cell-price="{ value }">
          {{ value == null ? '' : formatCurrency(value) }}
        </template>
      </DataTable>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import DataTable from '@/components/ui/DataTable.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { catalogApi } from '@/api/catalog'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency } from '@/utils/formatters'
import { catalogStatus } from '@/utils/statusLabels'

const tabs = [
  { key: 'specialties', label: 'Chuyên khoa' },
  { key: 'services', label: 'Dịch vụ' },
  { key: 'medicines', label: 'Thuốc' }
]
const activeTab = ref('specialties')
const filters = reactive({ keyword: '', status: '' })
const items = ref([])
const loading = ref(false)
const error = ref('')
const statusOptions = [
  { label: 'Hoạt động', value: 'ACTIVE' },
  { label: 'Ngừng dùng', value: 'INACTIVE' }
]

const columns = computed(() => {
  if (activeTab.value === 'services') {
    return [
      { key: 'code', label: 'Mã' },
      { key: 'name', label: 'Tên' },
      { key: 'durationMinutes', label: 'Thời lượng' },
      { key: 'price', label: 'Giá' },
      { key: 'status', label: 'Trạng thái' }
    ]
  }
  if (activeTab.value === 'medicines') {
    return [
      { key: 'code', label: 'Mã' },
      { key: 'name', label: 'Tên thuốc' },
      { key: 'unit', label: 'Đơn vị' },
      { key: 'status', label: 'Trạng thái' }
    ]
  }
  return [
    { key: 'code', label: 'Mã' },
    { key: 'name', label: 'Tên chuyên khoa' },
    { key: 'status', label: 'Trạng thái' }
  ]
})

const loadCatalog = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await catalogApi[activeTab.value].list({ ...filters })
    items.value = Array.isArray(response?.content) ? response.content : response || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

const selectTab = async (tab) => {
  activeTab.value = tab
  await loadCatalog()
}

onMounted(loadCatalog)
</script>
