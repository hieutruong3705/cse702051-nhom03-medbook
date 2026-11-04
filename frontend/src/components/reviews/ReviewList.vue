<template>
  <div>
    <p v-if="error" class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
    <ul v-else-if="reviews.length" class="space-y-3">
      <li v-for="review in reviews" :key="review.id" class="rounded-md border border-slate-200 bg-white p-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <StarRating :value="review.rating" />
            <span class="text-sm font-semibold text-slate-900">{{ review.reviewerName }}</span>
          </div>
          <span class="text-xs text-slate-600">
            {{ formatDate(review.createdAt) }}<span v-if="review.edited"> · đã sửa</span>
          </span>
        </div>
        <p v-if="showDoctor" class="mt-1 text-sm text-slate-600">
          Bác sĩ
          <RouterLink class="font-medium text-primary-700 underline" :to="`/doctors/${review.doctorId}`">{{ review.doctorName }}</RouterLink>
          <span v-if="review.specialtyName"> · {{ review.specialtyName }}</span>
        </p>
        <!-- Nội dung người dùng gõ: luôn hiển thị dạng văn bản (Vue tự mã hóa), không dùng v-html -->
        <p v-if="review.comment" class="mt-2 whitespace-pre-line text-sm text-slate-700">{{ review.comment }}</p>
      </li>
    </ul>
    <EmptyState v-else-if="!loading" :title="emptyText" />
  </div>
</template>

<script setup>
import EmptyState from '@/components/ui/EmptyState.vue'
import StarRating from './StarRating.vue'
import { formatDate } from '@/utils/formatters'

defineProps({
  reviews: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  showDoctor: { type: Boolean, default: false },
  emptyText: { type: String, default: 'Chưa có đánh giá nào' }
})
</script>
