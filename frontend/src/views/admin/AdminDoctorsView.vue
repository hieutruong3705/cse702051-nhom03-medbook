<template>
  <AdminLayout>
    <PageHeader
      title="Bác sĩ"
      eyebrow="Quản trị"
      description="Quản lý hồ sơ bác sĩ và trạng thái hoạt động. Không hiển thị nội dung bệnh án."
    >
      <template #actions>
        <BaseButton type="button" @click="openCreate">
          <i class="fa-solid fa-plus" aria-hidden="true"></i>
          Thêm bác sĩ
        </BaseButton>
      </template>
    </PageHeader>

    <section class="space-y-4 p-4 sm:p-6">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_220px_auto]" @submit.prevent="reload">
        <BaseInput id="admin-doctor-keyword" v-model="filters.keyword" label="Tìm kiếm" placeholder="Tên, mã, số giấy phép" />
        <BaseSelect id="admin-doctor-status" v-model="filters.status" label="Trạng thái" :options="statusOptions" />
        <BaseButton type="submit" variant="secondary" class="self-end">
          <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
          Lọc
        </BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có bác sĩ">
        <template #cell-name="{ row }">
          <div>
            <p class="font-semibold text-slate-900">{{ doctorName(row) }}</p>
            <p class="text-xs text-slate-500">{{ row.licenseNumber || row.license_number || 'Chưa có số giấy phép' }}</p>
          </div>
        </template>
        <template #cell-specialty="{ row }">
          {{ row.specialty?.name || row.specialtyName || '-' }}
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="doctorActive(row) ? 'ACTIVE' : 'INACTIVE'" :label="doctorActive(row) ? 'Hoạt động' : 'Ngừng hoạt động'" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap gap-2">
            <BaseButton type="button" size="sm" variant="secondary" @click="openEdit(row)">Sửa</BaseButton>
            <BaseButton type="button" size="sm" variant="danger" :disabled="removingId === row.id" @click="confirmRemove(row)">
              Ngừng hoạt động
            </BaseButton>
          </div>
        </template>
      </DataTable>

      <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
    </section>

    <FormModal v-model="formOpen" :title="editing ? 'Sửa hồ sơ bác sĩ' : 'Thêm bác sĩ'">
      <div class="grid gap-4 md:grid-cols-2">
        <BaseInput id="admin-doctor-name" v-model="form.fullName" label="Họ tên" required />
        <BaseInput id="admin-doctor-license" v-model="form.licenseNumber" label="Số giấy phép" required />
        <BaseInput id="admin-doctor-phone" v-model="form.phone" label="Số điện thoại" />
        <BaseInput id="admin-doctor-specialty" v-model="form.specialtyId" label="ID chuyên khoa" inputmode="numeric" />
        <div class="md:col-span-2">
          <BaseTextarea id="admin-doctor-bio" v-model="form.bio" label="Giới thiệu" rows="4" />
        </div>
      </div>
      <template #footer>
        <BaseButton type="button" variant="secondary" @click="formOpen = false">Hủy</BaseButton>
        <BaseButton type="button" @click="saveDoctor">Lưu</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="removeOpen"
      title="Ngừng hoạt động bác sĩ?"
      message="Nếu bác sĩ đã có lịch sử, server sẽ chuyển trạng thái ngừng hoạt động thay vì xóa."
      confirm-text="Xác nhận"
      danger
      @confirm="removeDoctor"
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
import { doctorsApi } from '@/api/doctors'
import { useToast } from '@/composables/useToast'
import { usePagination } from '@/composables/usePagination'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const columns = [
  { key: 'name', label: 'Bác sĩ' },
  { key: 'specialty', label: 'Chuyên khoa' },
  { key: 'phone', label: 'Điện thoại', formatter: (_, row) => row.phone || row.user?.phone || '-' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const statusOptions = [
  { label: 'Tất cả', value: '' },
  { label: 'Hoạt động', value: 'ACTIVE' },
  { label: 'Ngừng hoạt động', value: 'INACTIVE' }
]

const { success, error: toastError } = useToast()
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })
const filters = reactive({ keyword: '', status: '' })
const rows = ref([])
const loading = ref(false)
const error = ref('')
const formOpen = ref(false)
const removeOpen = ref(false)
const editing = ref(null)
const removingId = ref(null)
const selected = ref(null)
const form = reactive({ fullName: '', licenseNumber: '', phone: '', specialtyId: '', bio: '' })

const payload = computed(() => ({
  fullName: form.fullName,
  licenseNumber: form.licenseNumber,
  phone: form.phone,
  specialtyId: form.specialtyId ? Number(form.specialtyId) : null,
  bio: form.bio
}))

function doctorName(row) {
  return row.fullName || row.name || row.user?.fullName || row.userFullName || 'Chưa có tên'
}

function doctorActive(row) {
  return row.isActive ?? row.active ?? row.status === 'ACTIVE'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorsApi.listAdmin({ ...filters, page: page.value, size: size.value })
    rows.value = response.content ?? response.items ?? response
    setPageResponse(response)
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  load()
}

function changePage(next) {
  page.value = next
  load()
}

function resetForm() {
  Object.assign(form, { fullName: '', licenseNumber: '', phone: '', specialtyId: '', bio: '' })
}

function openCreate() {
  editing.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    fullName: doctorName(row),
    licenseNumber: row.licenseNumber || row.license_number || '',
    phone: row.phone || row.user?.phone || '',
    specialtyId: row.specialtyId || row.specialty?.id || '',
    bio: row.bio || ''
  })
  formOpen.value = true
}

async function saveDoctor() {
  try {
    if (editing.value) await doctorsApi.updateAdmin(editing.value.id, payload.value)
    else await doctorsApi.createAdmin(payload.value)
    success(editing.value ? 'Đã cập nhật bác sĩ' : 'Đã thêm bác sĩ')
    formOpen.value = false
    load()
  } catch (err) {
    toastError(apiErrorMessage(err))
    throw err
  }
}

function confirmRemove(row) {
  selected.value = row
  removeOpen.value = true
}

async function removeDoctor() {
  if (!selected.value) return
  removingId.value = selected.value.id
  try {
    await doctorsApi.removeAdmin(selected.value.id)
    success('Đã cập nhật trạng thái bác sĩ')
    load()
  } catch (err) {
    toastError(apiErrorMessage(err))
  } finally {
    removingId.value = null
  }
}

onMounted(load)
</script>
