<template>
  <AdminLayout>
    <PageHeader
      title="Bác sĩ"
      eyebrow="Quản trị"
      description="Quản lý hồ sơ bác sĩ và trạng thái hoạt động. Không hiển thị nội dung bệnh án."
    >
      <template #actions>
        <BaseButton icon="fa-solid fa-plus" @click="openCreate">Thêm hồ sơ bác sĩ</BaseButton>
      </template>
    </PageHeader>

    <section class="mt-4 space-y-4">
      <form class="grid gap-3 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_200px_200px_auto]" @submit.prevent="reload">
        <BaseInput id="admin-doctor-keyword" v-model="filters.keyword" label="Tìm kiếm" placeholder="Tên, chuyên khoa, số giấy phép" />
        <BaseSelect id="admin-doctor-specialty-filter" v-model="filters.specialtyId" label="Chuyên khoa" placeholder="Tất cả" :options="specialtyOptions" />
        <BaseSelect id="admin-doctor-status" v-model="filters.isActive" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <BaseButton type="submit" variant="secondary" class="self-end" icon="fa-solid fa-magnifying-glass">Lọc</BaseButton>
      </form>

      <DataTable :columns="columns" :rows="rows" :loading="loading" :error="error" empty-text="Chưa có bác sĩ phù hợp">
        <template #cell-name="{ row }">
          <p class="font-semibold text-slate-900">{{ row.fullName }}</p>
          <p class="text-xs text-slate-500">{{ row.username }} · {{ row.licenseNumber || 'Chưa có số giấy phép' }}</p>
        </template>
        <template #cell-status="{ row }">
          <StatusBadge :value="row.isActive ? 'ACTIVE' : 'INACTIVE'" :label="row.isActive ? 'Hoạt động' : 'Ngừng hoạt động'" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap gap-2">
            <BaseButton variant="secondary" :aria-label="`Sửa hồ sơ ${row.fullName}`" @click="openEdit(row)">Sửa</BaseButton>
            <BaseButton variant="danger" :disabled="!row.isActive" :aria-label="`Xóa hồ sơ ${row.fullName}`" @click="confirmRemove(row)">Xóa</BaseButton>
          </div>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </section>

    <FormModal v-model="formOpen" :title="editing ? 'Sửa hồ sơ bác sĩ' : 'Thêm hồ sơ bác sĩ'">
      <form class="grid gap-4 md:grid-cols-2" novalidate @submit.prevent="saveDoctor">
        <div v-if="!editing" class="md:col-span-2">
          <BaseSelect
            id="admin-doctor-user"
            v-model="form.userId"
            label="Tài khoản bác sĩ"
            required
            placeholder="Chọn tài khoản"
            :options="accountOptions"
            :hint="accountOptions.length ? 'Chỉ liệt kê tài khoản có vai trò bác sĩ và chưa có hồ sơ' : 'Chưa có tài khoản bác sĩ nào thiếu hồ sơ. Hãy tạo tài khoản ở mục Tài khoản trước.'"
            :error="formErrors.userId"
          />
        </div>
        <BaseInput id="admin-doctor-name" v-model="form.fullName" label="Họ tên" required :error="formErrors.fullName" />
        <BaseInput id="admin-doctor-license" v-model="form.licenseNumber" label="Số giấy phép hành nghề" required :error="formErrors.licenseNumber" />
        <BaseSelect id="admin-doctor-specialty" v-model="form.specialtyId" label="Chuyên khoa" required placeholder="Chọn chuyên khoa" :options="specialtyOptions" :error="formErrors.specialtyId" />
        <BaseInput id="admin-doctor-phone" v-model="form.phone" label="Số điện thoại" :error="formErrors.phone" />
        <div class="md:col-span-2">
          <BaseTextarea id="admin-doctor-bio" v-model="form.bio" label="Giới thiệu" :rows="4" :error="formErrors.bio" />
        </div>
      </form>
      <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ formError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="formOpen = false">Hủy</BaseButton>
        <BaseButton :loading="saving" @click="saveDoctor">Lưu</BaseButton>
      </template>
    </FormModal>

    <ConfirmDialog
      v-model="removeOpen"
      title="Xóa hồ sơ bác sĩ?"
      :message="`Nếu ${selected?.fullName || 'bác sĩ'} đã có lịch hẹn, ca làm việc hoặc lần khám thì hồ sơ chỉ chuyển sang ngừng hoạt động và các giờ trống sắp tới bị gỡ; lịch đã đặt được giữ nguyên.`"
      confirm-text="Xóa"
      danger
      :loading="removing"
      @confirm="removeDoctor"
    />
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
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
import { adminUsersApi } from '@/api/adminUsers'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams, fieldErrors } from '@/utils/params'

const columns = [
  { key: 'name', label: 'Bác sĩ' },
  { key: 'specialtyName', label: 'Chuyên khoa', formatter: (value) => value || '-' },
  { key: 'phone', label: 'Điện thoại', formatter: (value) => value || '-' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]
// Máy chủ lọc bằng tham số isActive (true/false), không phải status.
const statusOptions = [
  { label: 'Hoạt động', value: 'true' },
  { label: 'Ngừng hoạt động', value: 'false' }
]

const { success, error: toastError, info } = useToast()
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 10 })
const filters = reactive({ keyword: '', specialtyId: '', isActive: '' })
const rows = ref([])
const loading = ref(false)
const error = ref('')
const specialtyOptions = ref([])
const accountOptions = ref([])

const formOpen = ref(false)
const editing = ref(null)
const saving = ref(false)
const formError = ref('')
const formErrors = ref({})
const form = reactive({ userId: '', fullName: '', licenseNumber: '', phone: '', specialtyId: '', bio: '' })

const removeOpen = ref(false)
const removing = ref(false)
const selected = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await doctorsApi.listAdmin(cleanParams({ ...filters, page: page.value, size: size.value }))
    rows.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    rows.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

async function loadSpecialties() {
  try {
    const response = await catalogApi.specialties.list({ size: 100 })
    specialtyOptions.value = (response?.content ?? []).map((item) => ({ label: item.name, value: String(item.id) }))
  } catch {
    specialtyOptions.value = []
  }
}

// Hồ sơ bác sĩ chỉ tạo được cho tài khoản đã có vai trò DOCTOR và chưa có hồ sơ.
async function loadAccounts() {
  try {
    const response = await adminUsersApi.list({ role: 'DOCTOR', status: 'ACTIVE', size: 100 })
    accountOptions.value = (response?.content ?? [])
      .filter((user) => !user.doctorId)
      .map((user) => ({ label: `${user.fullName} (${user.username})`, value: String(user.id), fullName: user.fullName, phone: user.phone }))
  } catch {
    accountOptions.value = []
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

async function openCreate() {
  editing.value = null
  Object.assign(form, { userId: '', fullName: '', licenseNumber: '', phone: '', specialtyId: '', bio: '' })
  formError.value = ''
  formErrors.value = {}
  formOpen.value = true
  await loadAccounts()
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    userId: String(row.userId ?? ''),
    fullName: row.fullName || '',
    licenseNumber: row.licenseNumber || '',
    phone: row.phone || '',
    specialtyId: row.specialtyId ? String(row.specialtyId) : '',
    bio: row.bio || ''
  })
  formError.value = ''
  formErrors.value = {}
  formOpen.value = true
}

async function saveDoctor() {
  if (saving.value) return
  saving.value = true
  formError.value = ''
  formErrors.value = {}
  const body = {
    userId: form.userId ? Number(form.userId) : null,
    fullName: form.fullName.trim(),
    licenseNumber: form.licenseNumber.trim(),
    phone: form.phone.trim(),
    specialtyId: form.specialtyId ? Number(form.specialtyId) : null,
    bio: form.bio.trim()
  }
  try {
    if (editing.value) await doctorsApi.updateAdmin(editing.value.id, body)
    else await doctorsApi.createAdmin(body)
    success(editing.value ? 'Đã cập nhật hồ sơ bác sĩ' : 'Đã thêm hồ sơ bác sĩ')
    formOpen.value = false
    await load()
  } catch (err) {
    formErrors.value = fieldErrors(err)
    formError.value = apiErrorMessage(err)
  } finally {
    saving.value = false
  }
}

function confirmRemove(row) {
  selected.value = row
  removeOpen.value = true
}

async function removeDoctor() {
  if (!selected.value || removing.value) return
  removing.value = true
  try {
    const kept = await doctorsApi.removeAdmin(selected.value.id)
    if (kept) info(`${selected.value.fullName} đã có lịch sử nên hồ sơ chỉ chuyển sang ngừng hoạt động.`, 'Đã ngừng hoạt động')
    else success('Đã xóa hồ sơ bác sĩ')
    removeOpen.value = false
    await load()
  } catch (err) {
    toastError(apiErrorMessage(err))
  } finally {
    removing.value = false
  }
}

onMounted(() => {
  load()
  loadSpecialties()
})
</script>
