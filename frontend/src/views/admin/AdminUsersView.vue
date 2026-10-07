<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Tài khoản" eyebrow="Quản trị" description="Tạo tài khoản, khóa hoặc mở khóa và đổi vai trò. Khóa hay đổi vai trò làm phiên đăng nhập cũ của người đó hết hiệu lực ngay.">
        <template #actions>
          <BaseButton icon="fa-solid fa-user-plus" @click="openCreateForm">Tạo tài khoản</BaseButton>
        </template>
      </PageHeader>

      <form class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_180px_180px_auto]" @submit.prevent="reload">
        <BaseInput id="admin-user-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Họ tên, email, tên đăng nhập" />
        <BaseSelect id="admin-user-role" v-model="filters.role" label="Vai trò" placeholder="Tất cả" :options="roleOptions" />
        <BaseSelect id="admin-user-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <div class="flex items-end">
          <BaseButton type="submit" class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass">Lọc</BaseButton>
        </div>
      </form>

      <DataTable :columns="columns" :rows="users" :loading="loading" :error="error" empty-text="Chưa có tài khoản phù hợp">
        <template #cell-fullName="{ row }">
          <div class="font-semibold text-slate-950">{{ row.fullName }}</div>
          <div class="text-xs text-slate-500">{{ row.username }}</div>
        </template>
        <template #cell-roles="{ value }">
          <div class="flex flex-wrap gap-1">
            <span v-for="role in value || []" :key="role" class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
              {{ roleLabels[role] || role }}
            </span>
          </div>
        </template>
        <template #cell-status="{ value }">
          <StatusBadge :value="value === 'LOCKED' ? 'CANCELLED' : 'ACTIVE'" :label="userStatus[value] || value" />
        </template>
        <template #cell-actions="{ row }">
          <div class="flex flex-wrap gap-2">
            <BaseButton
              :variant="row.status === 'LOCKED' ? 'secondary' : 'danger'"
              :aria-label="`${row.status === 'LOCKED' ? 'Mở khóa' : 'Khóa'} tài khoản ${row.username}`"
              @click="openStatus(row)"
            >
              {{ row.status === 'LOCKED' ? 'Mở khóa' : 'Khóa' }}
            </BaseButton>
            <BaseButton variant="secondary" :aria-label="`Đổi vai trò của ${row.username}`" @click="openRoles(row)">Vai trò</BaseButton>
          </div>
        </template>
        <template #footer>
          <Pagination :page="page" :total-pages="totalPages" @update:page="changePage" />
        </template>
      </DataTable>
    </div>

    <!-- Tạo tài khoản -->
    <FormModal v-model="createOpen" title="Tạo tài khoản" description="Tài khoản bác sĩ được tạo kèm hồ sơ bác sĩ; tài khoản bệnh nhân được tạo kèm hồ sơ bệnh nhân và bệnh án.">
      <form class="grid gap-4 sm:grid-cols-2" novalidate @submit.prevent="createUser">
        <BaseInput id="create-full-name" v-model="createForm.fullName" label="Họ tên" required :error="createErrors.fullName" />
        <BaseInput id="create-email" v-model="createForm.email" label="Email" type="email" required :error="createErrors.email" />
        <BaseInput id="create-username" v-model="createForm.username" label="Tên đăng nhập" required autocomplete="off" :error="createErrors.username" />
        <BaseInput
          id="create-password"
          v-model="createForm.password"
          label="Mật khẩu"
          type="password"
          required
          autocomplete="new-password"
          hint="Từ 8 ký tự, có chữ và số"
          :error="createErrors.password"
        />
        <BaseInput id="create-phone" v-model="createForm.phone" label="Số điện thoại" :error="createErrors.phone" />
        <BaseSelect id="create-roles" v-model="createForm.role" label="Vai trò" :options="roleOptions" required :error="createErrors.role" />
        <template v-if="createForm.role === 'DOCTOR'">
          <BaseSelect
            id="create-doctor-specialty"
            v-model="createForm.specialtyId"
            label="Chuyên khoa"
            required
            placeholder="Chọn chuyên khoa"
            :options="specialtyOptions"
            :error="createErrors['doctorProfile.specialtyId'] || createErrors.specialtyId"
          />
          <BaseInput
            id="create-doctor-license"
            v-model="createForm.licenseNumber"
            label="Số giấy phép hành nghề"
            required
            :error="createErrors['doctorProfile.licenseNumber'] || createErrors.licenseNumber"
          />
        </template>
      </form>
      <p v-if="createError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ createError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="createOpen = false">Hủy</BaseButton>
        <BaseButton :loading="creating" @click="createUser">Tạo</BaseButton>
      </template>
    </FormModal>

    <!-- Khóa / mở khóa -->
    <FormModal
      v-model="statusOpen"
      :title="selected?.status === 'LOCKED' ? 'Mở khóa tài khoản?' : 'Khóa tài khoản?'"
      :description="selected?.status === 'LOCKED'
        ? `${selected?.username} sẽ đăng nhập lại được.`
        : `${selected?.username} sẽ bị đăng xuất ngay và không đăng nhập được cho tới khi mở khóa.`"
    >
      <BaseTextarea id="user-status-reason" v-model="statusReason" label="Lý do (lưu vào nhật ký hệ thống)" :rows="3" />
      <p v-if="actionError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ actionError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="statusOpen = false">Hủy</BaseButton>
        <BaseButton :variant="selected?.status === 'LOCKED' ? 'primary' : 'danger'" :loading="acting" @click="changeStatus">
          {{ selected?.status === 'LOCKED' ? 'Mở khóa' : 'Khóa tài khoản' }}
        </BaseButton>
      </template>
    </FormModal>

    <!-- Đổi vai trò -->
    <FormModal v-model="rolesOpen" title="Đổi vai trò" :description="`Chọn các vai trò của ${selected?.username || ''}. Phải có ít nhất một vai trò.`">
      <fieldset class="space-y-2">
        <legend class="sr-only">Vai trò</legend>
        <label v-for="option in roleOptions" :key="option.value" class="flex items-center gap-2 text-sm text-slate-800">
          <input :id="`role-${option.value}`" v-model="selectedRoles" type="checkbox" :value="option.value" class="h-4 w-4 rounded border-slate-300" />
          {{ option.label }}
        </label>
      </fieldset>
      <p class="mt-3 text-xs text-slate-500">Thêm vai trò bác sĩ không tự tạo hồ sơ bác sĩ: hãy tạo hồ sơ ở mục Bác sĩ sau khi lưu.</p>
      <p v-if="actionError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ actionError }}</p>
      <template #footer>
        <BaseButton variant="secondary" @click="rolesOpen = false">Hủy</BaseButton>
        <BaseButton :loading="acting" @click="changeRoles">Lưu vai trò</BaseButton>
      </template>
    </FormModal>
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
  DataTable,
  FormModal,
  PageHeader,
  Pagination,
  StatusBadge
} from '@/components/ui'
import { adminUsersApi } from '@/api/adminUsers'
import { catalogApi } from '@/api/catalog'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { cleanParams, fieldErrors } from '@/utils/params'

const roleLabels = { PATIENT: 'Bệnh nhân', DOCTOR: 'Bác sĩ', ADMIN: 'Quản trị viên' }
const userStatus = { ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa' }
const roleOptions = Object.entries(roleLabels).map(([value, label]) => ({ label, value }))
// Tài khoản chỉ có hai trạng thái: ACTIVE và LOCKED.
const statusOptions = Object.entries(userStatus).map(([value, label]) => ({ label, value }))
const columns = [
  { key: 'fullName', label: 'Người dùng' },
  { key: 'email', label: 'Email' },
  { key: 'roles', label: 'Vai trò' },
  { key: 'status', label: 'Trạng thái' },
  { key: 'actions', label: 'Thao tác' }
]

const filters = reactive({ keyword: '', role: '', status: '' })
const users = ref([])
const loading = ref(false)
const error = ref('')
const { page, size, totalPages, setPageResponse, reset } = usePagination({ size: 20 })
const { success } = useToast()

const emptyCreateForm = () => ({ fullName: '', email: '', username: '', password: '', phone: '', role: 'PATIENT', specialtyId: '', licenseNumber: '' })
const createForm = reactive(emptyCreateForm())
const createOpen = ref(false)
const creating = ref(false)
const createError = ref('')
const createErrors = ref({})
const specialtyOptions = ref([])

const selected = ref(null)
const statusOpen = ref(false)
const statusReason = ref('')
const rolesOpen = ref(false)
const selectedRoles = ref([])
const acting = ref(false)
const actionError = ref('')

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const response = await adminUsersApi.list(cleanParams({ ...filters, page: page.value, size: size.value }))
    users.value = response?.content ?? []
    setPageResponse(response)
  } catch (err) {
    users.value = []
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function reload() {
  reset()
  return loadUsers()
}

function changePage(next) {
  page.value = next
  loadUsers()
}

async function openCreateForm() {
  Object.assign(createForm, emptyCreateForm())
  createError.value = ''
  createErrors.value = {}
  createOpen.value = true
  if (!specialtyOptions.value.length) {
    try {
      const response = await catalogApi.specialties.list({ size: 100 })
      specialtyOptions.value = (response?.content ?? []).map((item) => ({ label: item.name, value: String(item.id) }))
    } catch {
      specialtyOptions.value = []
    }
  }
}

async function createUser() {
  if (creating.value) return
  creating.value = true
  createError.value = ''
  createErrors.value = {}
  const body = {
    username: createForm.username.trim(),
    password: createForm.password,
    fullName: createForm.fullName.trim(),
    email: createForm.email.trim(),
    phone: createForm.phone.trim(),
    role: createForm.role
  }
  if (createForm.role === 'DOCTOR') {
    body.doctorProfile = {
      specialtyId: createForm.specialtyId ? Number(createForm.specialtyId) : null,
      licenseNumber: createForm.licenseNumber.trim()
    }
  }
  try {
    await adminUsersApi.create(body)
    success('Đã tạo tài khoản')
    createOpen.value = false
    await loadUsers()
  } catch (err) {
    createErrors.value = fieldErrors(err)
    createError.value = apiErrorMessage(err)
  } finally {
    creating.value = false
  }
}

function openStatus(row) {
  selected.value = row
  statusReason.value = ''
  actionError.value = ''
  statusOpen.value = true
}

async function changeStatus() {
  if (!selected.value || acting.value) return
  acting.value = true
  actionError.value = ''
  const locking = selected.value.status !== 'LOCKED'
  try {
    await adminUsersApi.changeStatus(selected.value.id, { status: locking ? 'LOCKED' : 'ACTIVE', reason: statusReason.value.trim() })
    success(locking ? 'Đã khóa tài khoản' : 'Đã mở khóa tài khoản')
    statusOpen.value = false
    await loadUsers()
  } catch (err) {
    actionError.value = apiErrorMessage(err)
  } finally {
    acting.value = false
  }
}

function openRoles(row) {
  selected.value = row
  selectedRoles.value = [...(row.roles || [])]
  actionError.value = ''
  rolesOpen.value = true
}

async function changeRoles() {
  if (!selected.value || acting.value) return
  if (!selectedRoles.value.length) {
    actionError.value = 'Phải chọn ít nhất một vai trò.'
    return
  }
  acting.value = true
  actionError.value = ''
  try {
    await adminUsersApi.changeRoles(selected.value.id, { roles: selectedRoles.value })
    success('Đã cập nhật vai trò')
    rolesOpen.value = false
    await loadUsers()
  } catch (err) {
    actionError.value = apiErrorMessage(err)
  } finally {
    acting.value = false
  }
}

onMounted(loadUsers)
</script>
