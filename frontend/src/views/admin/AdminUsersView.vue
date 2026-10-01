<template>
  <AdminLayout>
    <div class="space-y-6">
      <PageHeader title="Tài khoản" description="Tìm kiếm và theo dõi tài khoản theo vai trò, trạng thái.">
        <template #actions>
          <BaseButton icon="fa-solid fa-user-plus" @click="openCreate = true">Tạo tài khoản</BaseButton>
        </template>
      </PageHeader>

      <section class="grid gap-4 rounded-md border border-slate-200 bg-white p-4 md:grid-cols-[1fr_180px_180px_auto]">
        <BaseInput id="admin-user-keyword" v-model="filters.keyword" label="Từ khóa" placeholder="Tên, email, username" />
        <BaseSelect id="admin-user-role" v-model="filters.role" label="Vai trò" placeholder="Tất cả" :options="roleOptions" />
        <BaseSelect id="admin-user-status" v-model="filters.status" label="Trạng thái" placeholder="Tất cả" :options="statusOptions" />
        <div class="flex items-end">
          <BaseButton class="w-full" :loading="loading" icon="fa-solid fa-magnifying-glass" @click="loadUsers">Lọc</BaseButton>
        </div>
      </section>

      <DataTable :columns="columns" :rows="users" :loading="loading" :error="error" empty-text="Chưa có tài khoản phù hợp">
        <template #cell-fullName="{ row }">
          <div class="font-semibold text-slate-950">{{ row.fullName }}</div>
          <div class="text-xs text-slate-500">{{ row.username }}</div>
        </template>
        <template #cell-roles="{ value }">
          <div class="flex flex-wrap gap-1">
            <StatusBadge v-for="role in value || []" :key="role" :value="role" :label="role" />
          </div>
        </template>
        <template #cell-status="{ value }">
          <StatusBadge :value="value || 'ACTIVE'" :label="catalogStatus[value] || value || 'ACTIVE'" />
        </template>
      </DataTable>

      <FormModal v-model="openCreate" title="Tạo tài khoản" description="Form này dùng hợp đồng API Admin users, chờ backend hoàn thiện.">
        <div class="grid gap-4 sm:grid-cols-2">
          <BaseInput id="create-full-name" v-model="createForm.fullName" label="Họ tên" required />
          <BaseInput id="create-email" v-model="createForm.email" label="Email" type="email" required />
          <BaseInput id="create-username" v-model="createForm.username" label="Username" required />
          <BaseInput id="create-password" v-model="createForm.password" label="Mật khẩu" type="password" required />
          <BaseSelect id="create-roles" v-model="createForm.role" label="Vai trò" :options="roleOptions" required />
        </div>
        <p v-if="createError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ createError }}</p>
        <template #footer>
          <BaseButton variant="secondary" @click="openCreate = false">Hủy</BaseButton>
          <BaseButton :loading="creating" @click="createUser">Tạo</BaseButton>
        </template>
      </FormModal>
    </div>
  </AdminLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import DataTable from '@/components/ui/DataTable.vue'
import FormModal from '@/components/ui/FormModal.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { adminUsersApi } from '@/api/adminUsers'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { catalogStatus } from '@/utils/statusLabels'

const filters = reactive({ keyword: '', role: '', status: '' })
const createForm = reactive({ fullName: '', email: '', username: '', password: '', role: 'PATIENT' })
const users = ref([])
const loading = ref(false)
const creating = ref(false)
const error = ref('')
const createError = ref('')
const openCreate = ref(false)

const roleOptions = [
  { label: 'PATIENT', value: 'PATIENT' },
  { label: 'DOCTOR', value: 'DOCTOR' },
  { label: 'ADMIN', value: 'ADMIN' }
]
const statusOptions = [
  { label: 'Hoạt động', value: 'ACTIVE' },
  { label: 'Khóa', value: 'LOCKED' },
  { label: 'Ngừng dùng', value: 'INACTIVE' }
]
const columns = [
  { key: 'fullName', label: 'Người dùng' },
  { key: 'email', label: 'Email' },
  { key: 'roles', label: 'Vai trò' },
  { key: 'status', label: 'Trạng thái' }
]

const loadUsers = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await adminUsersApi.list({ ...filters })
    users.value = Array.isArray(response?.content) ? response.content : response || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

const createUser = async () => {
  if (creating.value) return
  creating.value = true
  createError.value = ''
  try {
    await adminUsersApi.create({ ...createForm, roles: [createForm.role] })
    openCreate.value = false
    await loadUsers()
  } catch (err) {
    createError.value = apiErrorMessage(err)
  } finally {
    creating.value = false
  }
}

onMounted(loadUsers)
</script>
