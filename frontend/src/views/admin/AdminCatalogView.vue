<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Danh mục" eyebrow="Quản trị" description="Quản lý chuyên khoa, dịch vụ khám và thuốc dùng khi kê đơn.">
        <template #actions>
          <BaseButton icon="fa-solid fa-plus" @click="openCreate">Thêm {{ current.noun }}</BaseButton>
        </template>
      </PageHeader>

      <div class="rounded-md border border-slate-200 bg-white p-2" role="tablist" aria-label="Loại danh mục">
        <div class="grid gap-2 sm:grid-cols-3">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="activeTab === tab.key"
            class="rounded px-4 py-2 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600"
            :class="activeTab === tab.key ? 'bg-primary-600 text-white' : 'text-slate-700 hover:bg-slate-100'"
            @click="selectTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <form class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-[1fr_180px_auto]" @submit.prevent="reload">
        <BaseInput id="catalog-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Mã hoặc tên" />
        <BaseSelect id="catalog-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass">Lọc</BaseButton>
        </div>
      </form>

      <DataTable :columns="columns" :rows="items" :loading="loading" :error="error" :empty-text="`Chưa có ${current.noun} phù hợp`">
        <template #cell-status="{ value }">
          <StatusBadge :value="value || 'ACTIVE'" :label="catalogStatus[value] || value" />
        </template>
        <template #cell-price="{ value }">{{ value == null ? '' : formatCurrency(value) }}</template>
        <template #cell-durationMinutes="{ value }">{{ value ? `${value} phút` : '' }}</template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap gap-2">
            <BaseButton variant="secondary" :aria-label="`Sửa ${row.name}`" @click="openEdit(row)">Sửa</BaseButton>
            <BaseButton variant="danger" :aria-label="`Xóa ${row.name}`" @click="askRemove(row)">Xóa</BaseButton>
          </div>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </div>

    <FormModal v-model="formOpen" :title="editing ? `Sửa ${current.noun}` : `Thêm ${current.noun}`">
      <form id="catalog-form" class="grid gap-4 sm:grid-cols-2" novalidate @submit.prevent="save">
        <BaseInput
          id="catalog-code"
          v-model="form.code"
          label="Mã"
          required
          :disabled="Boolean(editing)"
          :hint="editing ? 'Mã không đổi được sau khi tạo' : 'Chữ, số, gạch dưới, gạch ngang'"
          :error="formErrors.code"
        />
        <BaseInput id="catalog-name" v-model="form.name" label="Tên" required :error="formErrors.name" />
        <template v-if="activeTab === 'services'">
          <BaseInput id="catalog-duration" v-model="form.durationMinutes" label="Thời lượng (phút)" type="number" required :error="formErrors.durationMinutes" />
          <BaseInput id="catalog-price" v-model="form.price" label="Giá (VND)" type="number" required :error="formErrors.price" />
        </template>
        <BaseInput v-if="activeTab === 'medicines'" id="catalog-unit" v-model="form.unit" label="Đơn vị" placeholder="Viên, gói, chai" :error="formErrors.unit" />
        <BaseSelect id="catalog-form-status" v-model="form.status" label="Trạng thái" :options="statusOptions" :error="formErrors.status" />
        <div class="sm:col-span-2">
          <BaseTextarea id="catalog-description" v-model="form.description" label="Mô tả" :rows="3" :error="formErrors.description" />
        </div>
      </form>
      <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="formOpen = false">Hủy</BaseButton>
        <BaseButton :loading="saving" @click="save">Lưu</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="removeOpen"
      :title="`Xóa ${current.noun}?`"
      :message="`Nếu “${selected?.name || ''}” đang được dùng ở nơi khác (hồ sơ bác sĩ, lịch hẹn, hóa đơn, đơn thuốc) thì hệ thống chỉ chuyển sang ngừng dùng để dữ liệu cũ vẫn tra được.`"
      confirm-text="Xóa"
      danger
      :loading="removing"
      @confirm="remove"
    />
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import {
  BaseButton,
  BaseInput,
  BaseSelect,
  BaseTextarea,
  ConfirmDialog,
  DataTable,
  FormModal,
  PageHeader,
  Pagination,
  StatusBadge
} from '@/components/ui'
import { catalogApi } from '@/api/catalog'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency } from '@/utils/formatters'
import { cleanParams, fieldErrors } from '@/utils/params'
import { catalogStatus } from '@/utils/statusLabels'

const tabs = [
  { key: 'specialties', label: 'Chuyên khoa', noun: 'chuyên khoa' },
  { key: 'services', label: 'Dịch vụ', noun: 'dịch vụ' },
  { key: 'medicines', label: 'Thuốc', noun: 'thuốc' }
]
const statusOptions = [
  { label: 'Hoạt động', value: 'ACTIVE' },
  { label: 'Ngừng dùng', value: 'INACTIVE' }
]

const activeTab = ref('specialties')
const current = computed(() => tabs.find((tab) => tab.key === activeTab.value))
const filters = reactive({ keyword: '', status: '' })
const items = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })
const { success, error: toastError, info } = useToast()

const formOpen = ref(false)
const editing = ref(null)
const saving = ref(false)
const formError = ref('')
const formErrors = ref({})
const form = reactive({ code: '', name: '', description: '', status: 'ACTIVE', durationMinutes: 30, price: '', unit: '' })

const removeOpen = ref(false)
const removing = ref(false)
const selected = ref(null)

const columns = computed(() => {
  const specific = {
    specialties: [],
    services: [
      { key: 'durationMinutes', label: 'Thời lượng' },
      { key: 'price', label: 'Giá' }
    ],
    medicines: [{ key: 'unit', label: 'Đơn vị' }]
  }[activeTab.value]
  return [
    { key: 'code', label: 'Mã' },
    { key: 'name', label: 'Tên' },
    ...specific,
    { key: 'status', label: 'Trạng thái' },
    { key: 'actions', label: 'Thao tác' }
  ]
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await catalogApi[activeTab.value].listAdmin(cleanParams({ ...filters, page: page.value, size: size.value }))
    items.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    items.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return load()
}

function changePage(next) {
  page.value = next
  load()
}

function selectTab(tab) {
  activeTab.value = tab
  filters.keyword = ''
  filters.status = ''
  return reload()
}

function openCreate() {
  editing.value = null
  Object.assign(form, { code: '', name: '', description: '', status: 'ACTIVE', durationMinutes: 30, price: '', unit: '' })
  formError.value = ''
  formErrors.value = {}
  formOpen.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    code: row.code || '',
    name: row.name || '',
    description: row.description || '',
    status: row.status || 'ACTIVE',
    durationMinutes: row.durationMinutes ?? 30,
    price: row.price ?? '',
    unit: row.unit || ''
  })
  formError.value = ''
  formErrors.value = {}
  formOpen.value = true
}

function payload() {
  const body = { code: form.code.trim(), name: form.name.trim(), description: form.description.trim(), status: form.status }
  if (activeTab.value === 'services') {
    body.durationMinutes = form.durationMinutes === '' ? null : Number(form.durationMinutes)
    body.price = form.price === '' ? null : Number(form.price)
  }
  if (activeTab.value === 'medicines') body.unit = form.unit.trim()
  return body
}

async function save() {
  if (saving.value) return
  saving.value = true
  formError.value = ''
  formErrors.value = {}
  try {
    const api = catalogApi[activeTab.value]
    if (editing.value) await api.update(editing.value.id, payload())
    else await api.create(payload())
    success(editing.value ? `Đã cập nhật ${current.value.noun}` : `Đã thêm ${current.value.noun}`)
    formOpen.value = false
    await load()
  } catch (err) {
    formErrors.value = fieldErrors(err)
    formError.value = apiErrorMessage(err)
  } finally {
    saving.value = false
  }
}

function askRemove(row) {
  selected.value = row
  removeOpen.value = true
}

async function remove() {
  if (!selected.value || removing.value) return
  removing.value = true
  try {
    const kept = await catalogApi[activeTab.value].remove(selected.value.id)
    if (kept) info(`“${selected.value.name}” đang được tham chiếu nên chỉ chuyển sang ngừng dùng, không xóa hẳn.`, 'Đã ngừng dùng')
    else success(`Đã xóa ${current.value.noun}`)
    removeOpen.value = false
    await load()
  } catch (err) {
    toastError(apiErrorMessage(err))
  } finally {
    removing.value = false
  }
}

onMounted(load)
</script>
