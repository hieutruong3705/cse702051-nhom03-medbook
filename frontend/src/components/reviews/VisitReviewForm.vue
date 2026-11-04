<template>
  <section class="mt-6 rounded-md border border-slate-200 bg-white p-4 sm:p-6" aria-labelledby="visit-review-title">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h2 id="visit-review-title" class="text-lg font-semibold text-slate-950">Đánh giá lần khám</h2>
      <StatusBadge v-if="review" :value="review.status" :label="reviewStatus[review.status] || review.status" />
    </div>

    <LoadingSpinner v-if="loading" class="mt-4" label="Đang tải đánh giá" />

    <!-- Đã có đánh giá và không sửa: hiện nội dung đã gửi -->
    <div v-else-if="review && !editing" class="mt-4" data-test="review-view">
      <StarRating :value="review.rating" size="lg" show-value />
      <p v-if="review.comment" class="mt-2 whitespace-pre-line text-sm text-slate-700">{{ review.comment }}</p>
      <p class="mt-2 text-xs text-slate-600">Gửi lúc {{ formatDateTime(review.createdAt) }}</p>
      <p v-if="review.status === 'HIDDEN'" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-800" role="status">
        Quản trị viên đã ẩn đánh giá này: {{ review.hiddenReason }}
      </p>
      <div v-else class="mt-4 flex flex-wrap gap-3">
        <BaseButton variant="secondary" icon="fa-regular fa-pen-to-square" @click="startEdit">Sửa đánh giá</BaseButton>
        <BaseButton variant="danger" icon="fa-regular fa-trash-can" :loading="saving" @click="confirmOpen = true">Xóa đánh giá</BaseButton>
      </div>
    </div>

    <!-- Chưa có đánh giá hoặc đang sửa -->
    <form v-else class="mt-4 space-y-4" data-test="review-form" novalidate @submit.prevent="submit">
      <p v-if="!review" class="text-sm text-slate-600">
        Chia sẻ cảm nhận về lần khám với {{ doctorName }}. Đánh giá hiện trên trang của bác sĩ với tên đã rút gọn.
      </p>
      <StarRating id="visit-rating" v-model="form.rating" editable required :error="errors.rating" />
      <BaseTextarea
        id="visit-comment"
        v-model="form.comment"
        label="Nhận xét (không bắt buộc)"
        :rows="3"
        hint="Tối đa 1000 ký tự. Không ghi thông tin sức khỏe hay số điện thoại của bạn."
        :error="errors.comment || (form.comment.length > 1000 ? 'Nhận xét tối đa 1000 ký tự' : '')"
      />
      <p v-if="submitError" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ submitError }}</p>
      <div class="flex flex-wrap gap-3">
        <BaseButton type="submit" :loading="saving" :disabled="form.comment.length > 1000" icon="fa-regular fa-paper-plane">
          {{ review ? 'Lưu thay đổi' : 'Gửi đánh giá' }}
        </BaseButton>
        <BaseButton v-if="review" variant="secondary" :disabled="saving" @click="editing = false">Hủy sửa</BaseButton>
      </div>
    </form>

    <ConfirmDialog
      v-model="confirmOpen"
      title="Xóa đánh giá"
      message="Đánh giá sẽ bị gỡ khỏi trang của bác sĩ. Bạn có thể gửi lại đánh giá mới sau đó."
      confirm-text="Xóa"
      danger
      :loading="saving"
      @confirm="remove"
    />
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseTextarea, ConfirmDialog, LoadingSpinner, StatusBadge } from '@/components/ui'
import StarRating from './StarRating.vue'
import { reviewsApi } from '@/api/reviews'
import { useToast } from '@/composables/useToast'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { formatDateTime } from '@/utils/formatters'
import { fieldErrors } from '@/utils/params'
import { reviewStatus } from '@/utils/statusLabels'

const props = defineProps({
  appointmentId: { type: [Number, String], required: true },
  doctorName: { type: String, default: 'bác sĩ' }
})

const toast = useToast()
const review = ref(null)
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const confirmOpen = ref(false)
const submitError = ref('')
const errors = reactive({ rating: '', comment: '' })
const form = reactive({ rating: 0, comment: '' })

async function load() {
  loading.value = true
  try {
    review.value = await reviewsApi.forAppointment(props.appointmentId)
  } catch {
    // 404: lần khám chưa có đánh giá → hiện form gửi mới
    review.value = null
  } finally {
    loading.value = false
  }
}

function startEdit() {
  form.rating = review.value.rating
  form.comment = review.value.comment || ''
  submitError.value = ''
  editing.value = true
}

async function submit() {
  errors.rating = form.rating ? '' : 'Vui lòng chọn số sao'
  errors.comment = ''
  submitError.value = ''
  if (errors.rating || saving.value) return
  saving.value = true
  try {
    const payload = { rating: form.rating, comment: form.comment.trim() || null }
    review.value = review.value
      ? await reviewsApi.update(review.value.id, payload)
      : await reviewsApi.create(props.appointmentId, payload)
    editing.value = false
    toast.success('Cảm ơn bạn đã đánh giá lần khám')
  } catch (err) {
    const details = fieldErrors(err)
    errors.rating = details.rating || ''
    errors.comment = details.comment || ''
    submitError.value = apiErrorMessage(err)
    // 409: lần khám vừa được đánh giá ở thẻ khác hoặc đánh giá vừa bị ẩn → tải lại để hiện đúng thực tế
    if (err?.status === 409) await load()
  } finally {
    saving.value = false
  }
}

async function remove() {
  saving.value = true
  try {
    await reviewsApi.remove(review.value.id)
    confirmOpen.value = false
    review.value = null
    form.rating = 0
    form.comment = ''
    toast.success('Đã xóa đánh giá')
  } catch (err) {
    toast.error(apiErrorMessage(err))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
