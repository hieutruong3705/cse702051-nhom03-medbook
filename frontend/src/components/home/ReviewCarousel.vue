<template>
  <!-- Băng chuyền theo mẫu WAI-ARIA APG: tự chuyển chỉ khi người dùng không bật "giảm chuyển động", có nút dừng
       (WCAG 2.2.2), dừng khi rê chuột hoặc khi focus nằm trong băng chuyền. -->
  <section
    v-if="reviews.length"
    aria-roledescription="băng chuyền"
    aria-labelledby="review-carousel-title"
    data-test="review-carousel"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
    @focusin="focusInside = true"
    @focusout="onFocusOut"
  >
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h2 id="review-carousel-title" class="text-2xl font-bold text-slate-950">Người bệnh nói gì</h2>
      <div class="flex items-center gap-2">
        <button
          v-if="canRotate"
          type="button"
          class="inline-flex h-9 items-center gap-2 rounded-md border border-slate-300 bg-white px-3 text-sm font-medium text-slate-800 hover:border-primary-600 hover:text-primary-700"
          :aria-label="playing ? 'Tạm dừng tự chuyển đánh giá' : 'Bật tự chuyển đánh giá'"
          data-test="carousel-toggle"
          @click="userPaused = !userPaused"
        >
          <i :class="playing ? 'fa-solid fa-pause' : 'fa-solid fa-play'" aria-hidden="true"></i>
          <span aria-hidden="true">{{ playing ? 'Tạm dừng' : 'Tự chuyển' }}</span>
        </button>
        <button
          type="button"
          class="inline-flex h-9 w-9 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-800 hover:border-primary-600 hover:text-primary-700 disabled:opacity-50"
          aria-label="Đánh giá trước"
          :disabled="reviews.length < 2"
          data-test="carousel-prev"
          @click="move(-1)"
        >
          <i class="fa-solid fa-chevron-left" aria-hidden="true"></i>
        </button>
        <button
          type="button"
          class="inline-flex h-9 w-9 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-800 hover:border-primary-600 hover:text-primary-700 disabled:opacity-50"
          aria-label="Đánh giá sau"
          :disabled="reviews.length < 2"
          data-test="carousel-next"
          @click="move(1)"
        >
          <i class="fa-solid fa-chevron-right" aria-hidden="true"></i>
        </button>
        <RouterLink class="ml-1 text-sm font-semibold text-primary-700 hover:text-primary-900" to="/reviews">Xem tất cả</RouterLink>
      </div>
    </div>

    <!-- Khi đang tự chuyển thì không đọc từng lần đổi (aria-live off) để trình đọc màn hình không bị ngắt liên tục -->
    <div class="mt-6 grid gap-4 md:grid-cols-3" :aria-live="playing ? 'off' : 'polite'" data-test="carousel-slides">
      <div
        v-for="(item, position) in visible"
        :key="item.review.id"
        :class="['rounded-md border border-slate-200 bg-white p-5 shadow-sm', position > 0 ? 'hidden md:block' : '']"
        role="group"
        aria-roledescription="đánh giá"
        :aria-label="`${item.index + 1} trên ${reviews.length}`"
        data-test="carousel-slide"
      >
        <StarRating :value="item.review.rating" />
        <blockquote class="mt-3 line-clamp-4 text-sm leading-6 text-slate-800">“{{ item.review.comment || 'Người bệnh không để lại nhận xét.' }}”</blockquote>
        <p class="mt-3 text-sm font-semibold text-slate-900">{{ item.review.reviewerName }}</p>
        <p class="text-xs text-slate-600">
          Khám với
          <RouterLink class="font-medium text-primary-700 underline" :to="`/doctors/${item.review.doctorId}`">{{ item.review.doctorName }}</RouterLink>
          <span v-if="item.review.visitDate"> · {{ formatDate(item.review.visitDate) }}</span>
        </p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import StarRating from '@/components/reviews/StarRating.vue'
import { formatDate } from '@/utils/formatters'

const props = defineProps({
  reviews: { type: Array, default: () => [] },
  intervalMs: { type: Number, default: 7000 }
})

const start = ref(0)
const hovering = ref(false)
const focusInside = ref(false)
const userPaused = ref(false)
const reducedMotion = ref(false)
let timer = null

// Chỉ tự chuyển khi có nhiều đánh giá hơn số thẻ đang hiện trên màn hình rộng
const canRotate = computed(() => props.reviews.length > 3)
const playing = computed(() => canRotate.value && !userPaused.value && !hovering.value && !focusInside.value)

const visible = computed(() => {
  const count = Math.min(3, props.reviews.length)
  return Array.from({ length: count }, (_, i) => {
    const index = (start.value + i) % props.reviews.length
    return { index, review: props.reviews[index] }
  })
})

function move(step) {
  const n = props.reviews.length
  if (n < 2) return
  start.value = (start.value + step + n) % n
}

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) focusInside.value = false
}

function schedule() {
  clearInterval(timer)
  timer = null
  if (playing.value) timer = setInterval(() => move(1), props.intervalMs)
}

watch(playing, schedule)
watch(() => props.reviews.length, () => {
  start.value = 0
  schedule()
})

onMounted(() => {
  // Người dùng bật "giảm chuyển động": mặc định không tự chuyển, vẫn bấm được nút "Tự chuyển"
  reducedMotion.value = Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
  if (reducedMotion.value) userPaused.value = true
  schedule()
})
onBeforeUnmount(() => clearInterval(timer))

defineExpose({ playing })
</script>
