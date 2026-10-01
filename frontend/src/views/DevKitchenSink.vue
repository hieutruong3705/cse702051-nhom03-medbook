<template>
  <DashboardLayout title="Kitchen sink" eyebrow="DEV" :items="[]">
    <div class="space-y-6">
      <PageHeader title="Component dùng chung" description="Mẫu nhanh cho form, bảng, modal và trạng thái." />

      <section class="grid gap-4 lg:grid-cols-2">
        <div class="rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold">Form</h2>
          <div class="mt-4 space-y-4">
            <BaseInput id="demo-name" v-model="form.name" label="Họ tên" required />
            <BaseSelect id="demo-status" v-model="form.status" label="Trạng thái" :options="statusOptions" />
            <BaseTextarea id="demo-note" v-model="form.note" label="Ghi chú" />
            <FileUpload id="demo-file" @update:file="file = $event" />
            <BaseButton icon="fa-solid fa-floppy-disk" @click="toast.success('Đã lưu mẫu')">Lưu</BaseButton>
          </div>
        </div>

        <div class="rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold">Modal</h2>
          <div class="mt-4 flex gap-2">
            <BaseButton variant="secondary" @click="modalOpen = true">Mở modal</BaseButton>
            <BaseButton variant="danger" @click="confirmOpen = true">Xóa</BaseButton>
          </div>
        </div>
      </section>

      <DataTable :columns="columns" :rows="rows">
        <template #cell-status="{ value }">
          <StatusBadge :value="value" />
        </template>
      </DataTable>

      <FormModal v-model="modalOpen" title="Modal mẫu" description="Đóng bằng Esc hoặc nút đóng.">
        <p class="text-sm text-slate-600">Nội dung modal.</p>
        <template #footer>
          <BaseButton variant="secondary" @click="modalOpen = false">Đóng</BaseButton>
        </template>
      </FormModal>
      <ConfirmDialog v-model="confirmOpen" danger title="Xóa dữ liệu" message="Thao tác này cần xác nhận." @confirm="confirmOpen = false" />
    </div>
  </DashboardLayout>
</template>

<script setup>
import { reactive, ref } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import DataTable from '@/components/ui/DataTable.vue'
import FileUpload from '@/components/ui/FileUpload.vue'
import FormModal from '@/components/ui/FormModal.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import DashboardLayout from '@/layouts/DashboardLayout.vue'
import { useToast } from '@/composables/useToast'

const toast = useToast()
const modalOpen = ref(false)
const confirmOpen = ref(false)
const file = ref(null)
const form = reactive({ name: '', status: 'ACTIVE', note: '' })
const statusOptions = [{ label: 'Hoạt động', value: 'ACTIVE' }, { label: 'Ngừng dùng', value: 'INACTIVE' }]
const columns = [{ key: 'name', label: 'Tên' }, { key: 'status', label: 'Trạng thái' }]
const rows = [{ id: 1, name: 'Khám tổng quát', status: 'ACTIVE' }]
</script>
