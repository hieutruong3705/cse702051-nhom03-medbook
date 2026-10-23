<template>
  <section class="space-y-4" aria-labelledby="inv-title">
    <h2 id="inv-title" class="text-lg font-semibold text-slate-950">Hóa đơn</h2>

    <LoadingSpinner v-if="loading" label="Đang tải hóa đơn" />

    <div v-else-if="loadError" class="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert" data-test="inv-load-error">
      <p class="font-semibold">{{ loadError }}</p>
      <BaseButton class="mt-3" variant="secondary" @click="load">Thử lại</BaseButton>
    </div>

    <!-- Đã có hóa đơn -->
    <div v-else-if="invoice" class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" data-test="invoice-view">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 class="font-semibold text-slate-950">Hóa đơn {{ invoice.invoiceCode }}</h3>
          <p class="text-xs text-slate-500">Lập lúc {{ formatDateTime(invoice.issuedAt) }}</p>
        </div>
        <StatusBadge :value="invoice.status" :label="invoiceStatus[invoice.status] || invoice.status" />
      </div>
      <div class="mt-4 overflow-x-auto">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
          <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-600">
            <tr>
              <th scope="col" class="px-3 py-2">Nội dung</th>
              <th scope="col" class="px-3 py-2 text-right">Số lượng</th>
              <th scope="col" class="px-3 py-2 text-right">Đơn giá</th>
              <th scope="col" class="px-3 py-2 text-right">Thành tiền</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in invoiceItems" :key="item.id">
              <td class="px-3 py-2 text-slate-900">{{ item.description }}</td>
              <td class="px-3 py-2 text-right">{{ item.quantity }}</td>
              <td class="px-3 py-2 text-right">{{ formatCurrency(item.unitPrice) }}</td>
              <td class="px-3 py-2 text-right">{{ formatCurrency(item.lineTotal) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <dl class="mt-4 space-y-1 text-sm">
        <div class="flex justify-between"><dt class="text-slate-500">Tạm tính</dt><dd>{{ formatCurrency(invoice.subtotal) }}</dd></div>
        <div class="flex justify-between"><dt class="text-slate-500">Giảm giá</dt><dd>{{ formatCurrency(invoice.discountAmount) }}</dd></div>
        <div class="flex justify-between border-t border-slate-100 pt-2 text-base font-semibold text-slate-950"><dt>Tổng cộng</dt><dd>{{ formatCurrency(invoice.totalAmount) }}</dd></div>
      </dl>
    </div>

    <!-- Chưa có hóa đơn: lập mới -->
    <form v-else class="rounded-md border border-slate-200 bg-white p-4 sm:p-6" novalidate data-test="invoice-form" @submit.prevent="submit">
      <p class="text-sm text-slate-600">Chọn dịch vụ đã thực hiện. Đơn giá do hệ thống lấy từ danh mục dịch vụ.</p>

      <div class="mt-4 space-y-3">
        <div v-for="(line, index) in lines" :key="line.key" class="grid items-end gap-3 sm:grid-cols-[1fr_120px_auto]" :data-test="`invoice-line-${index}`">
          <BaseSelect
            :id="`inv-service-${index}`"
            v-model="line.serviceId"
            label="Dịch vụ"
            placeholder="Chọn dịch vụ"
            :options="serviceOptions"
            :error="line.error"
          />
          <BaseInput :id="`inv-quantity-${index}`" v-model="line.quantity" label="Số lượng" type="number" />
          <BaseButton v-if="lines.length > 1" type="button" variant="secondary" :aria-label="`Bỏ dòng ${index + 1}`" @click="removeLine(index)">Bỏ</BaseButton>
        </div>
      </div>
      <BaseButton class="mt-3" type="button" variant="secondary" icon="fa-solid fa-plus" @click="addLine">Thêm dòng</BaseButton>

      <div class="mt-5 grid gap-3 sm:max-w-xs">
        <BaseInput id="inv-discount" v-model="discount" label="Giảm giá (VND)" type="number" :error="discountError" />
      </div>

      <dl class="mt-5 space-y-1 text-sm" data-test="invoice-preview">
        <div class="flex justify-between"><dt class="text-slate-500">Tạm tính</dt><dd>{{ formatCurrency(subtotal) }}</dd></div>
        <div class="flex justify-between"><dt class="text-slate-500">Giảm giá</dt><dd>{{ formatCurrency(discountValue) }}</dd></div>
        <div class="flex justify-between border-t border-slate-100 pt-2 text-base font-semibold text-slate-950"><dt>Tổng cộng (dự kiến)</dt><dd>{{ formatCurrency(Math.max(subtotal - discountValue, 0)) }}</dd></div>
      </dl>
      <p class="mt-1 text-xs text-slate-500">Số tiền chính thức do máy chủ tính khi lập hóa đơn.</p>

      <p v-if="formError" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-test="invoice-error">{{ formError }}</p>
      <div class="mt-5 flex justify-end">
        <BaseButton type="submit" :loading="saving">Lập hóa đơn</BaseButton>
      </div>
    </form>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { BaseButton, BaseInput, BaseSelect, LoadingSpinner, StatusBadge } from '@/components/ui'
import { catalogApi } from '@/api/catalog'
import { invoicesApi } from '@/api/invoices'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatCurrency, formatDateTime } from '@/utils/formatters'
import { invoiceStatus } from '@/utils/statusLabels'

const props = defineProps({
  encounterId: { type: [Number, String], required: true }
})

const toast = useToast()

const invoice = ref(null)
const invoiceItems = ref([])
const services = ref([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const formError = ref('')

let lineKey = 0
const newLine = () => ({ key: ++lineKey, serviceId: '', quantity: '1', error: '' })
const lines = ref([newLine()])
const discount = ref('0')
const discountError = ref('')

const list = (response) => (Array.isArray(response) ? response : (response?.content ?? []))
const serviceOptions = computed(() =>
  services.value.map((service) => ({ label: `${service.name} — ${formatCurrency(service.price)}`, value: String(service.id) }))
)
const priceOf = (serviceId) => Number(services.value.find((service) => String(service.id) === String(serviceId))?.price ?? 0)

const subtotal = computed(() =>
  lines.value.reduce((sum, line) => sum + priceOf(line.serviceId) * (Number(line.quantity) > 0 ? Number(line.quantity) : 0), 0)
)
const discountValue = computed(() => (Number(discount.value) > 0 ? Number(discount.value) : 0))

function addLine() {
  lines.value = [...lines.value, newLine()]
}

function removeLine(index) {
  lines.value = lines.value.filter((_, position) => position !== index)
}

async function load() {
  loading.value = true
  loadError.value = ''
  invoice.value = null
  try {
    try {
      invoice.value = await invoicesApi.byEncounter(props.encounterId)
      invoiceItems.value = list(await invoicesApi.items(invoice.value.id))
    } catch (err) {
      if (err?.status !== 404) throw err
      // 404: lần khám chưa có hóa đơn → hiện biểu mẫu lập mới cùng danh mục dịch vụ
      // size 100: lấy cả danh mục (mặc định máy chủ chỉ trả 20 dịch vụ đầu)
      services.value = list(await catalogApi.services.list({ size: 100 }))
    }
  } catch (err) {
    loadError.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function validate() {
  let ok = true
  formError.value = ''
  discountError.value = ''
  lines.value.forEach((line) => {
    line.error = ''
    if (!line.serviceId) {
      line.error = 'Phải chọn dịch vụ'
      ok = false
    } else if (!(Number(line.quantity) > 0)) {
      line.error = 'Số lượng phải lớn hơn 0'
      ok = false
    } else if (!Number.isInteger(Number(line.quantity)) || Number(line.quantity) > 100) {
      line.error = 'Số lượng phải là số nguyên từ 1 đến 100'
      ok = false
    }
  })
  if (discount.value !== '' && Number(discount.value) < 0) {
    discountError.value = 'Giảm giá không được âm'
    ok = false
  } else if (discountValue.value > subtotal.value) {
    discountError.value = 'Giảm giá không được lớn hơn tạm tính'
    ok = false
  }
  return ok
}

async function submit() {
  if (saving.value || !validate()) return
  saving.value = true
  try {
    const items = lines.value.map((line) => ({ serviceId: Number(line.serviceId), quantity: Number(line.quantity) }))
    await invoicesApi.createForEncounter(props.encounterId, items, { discountAmount: discountValue.value })
    toast.success('Đã lập hóa đơn')
    await load()
  } catch (err) {
    formError.value = apiErrorMessage(err)
    // 409: hóa đơn vừa được lập ở nơi khác → tải lại để hiển thị
    if (err?.status === 409) await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
