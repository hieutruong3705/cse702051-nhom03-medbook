<template>
  <!-- Chỉ trên điện thoại (dưới 768 px) và chỉ khi khối lịch khám đã cuộn khỏi màn hình, để không hiện hai lần -->
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="translate-y-full opacity-0"
    leave-active-class="transition duration-150 ease-in"
    leave-to-class="translate-y-full opacity-0"
  >
    <div
      v-if="visible"
      class="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 shadow-[0_-6px_16px_rgba(15,23,42,0.08)] md:hidden"
      role="region"
      aria-label="Đặt lịch với bác sĩ"
      data-test="mobile-booking-bar"
    >
      <div class="mx-auto flex max-w-xl items-center gap-3">
        <DoctorAvatar :doctor-id="doctor.id" :src="doctor.avatarUrl" size="xs" />
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-semibold text-slate-950">{{ doctor.fullName }}</p>
          <p class="truncate text-xs text-slate-700" data-test="mobile-bar-slot">
            <template v-if="earliest">
              Sớm nhất: <span class="font-semibold text-emerald-800">{{ formatTime(earliest.slot.startTime) }}</span> · {{ earliest.label }}
            </template>
            <template v-else>Xem lịch khám 7 ngày tới</template>
          </p>
        </div>
        <RouterLink
          v-if="canBook && earliest"
          class="shrink-0 rounded-md bg-primary-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-600 focus-visible:ring-offset-2"
          :to="earliest.link"
          :aria-label="`Đặt lịch ${formatTime(earliest.slot.startTime)} ${earliest.label} với ${doctor.fullName}`"
          data-test="mobile-bar-book"
        >
          Đặt lịch
        </RouterLink>
        <button
          v-else
          type="button"
          class="shrink-0 rounded-md border border-primary-600 px-4 py-2.5 text-sm font-semibold text-primary-700 hover:bg-primary-50"
          data-test="mobile-bar-schedule"
          @click="goToSchedule"
        >
          Xem lịch
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DoctorAvatar from './DoctorAvatar.vue'
import { formatTime } from '@/utils/formatters'
import { prefersReducedMotion } from '@/utils/motion'

const props = defineProps({
  doctor: { type: Object, required: true },
  // { slot, date, label, link } từ khối lịch khám; null khi ngày đang xem hết lịch
  earliest: { type: Object, default: null },
  canBook: { type: Boolean, default: true },
  // Phần tử chứa khối lịch khám: thanh ẩn khi phần tử này đang trong màn hình
  target: { type: Object, default: null }
})

const BODY_CLASS = 'has-mobile-booking-bar'
const visible = ref(true)
let observer = null

function observe(el) {
  observer?.disconnect()
  observer = null
  if (!el || typeof window === 'undefined' || !('IntersectionObserver' in window)) {
    visible.value = true
    return
  }
  observer = new IntersectionObserver(([entry]) => {
    visible.value = !entry.isIntersecting
  })
  observer.observe(el)
}

/** Đưa người dùng về khối lịch khám và đặt focus vào ngày đang chọn để chọn tiếp bằng bàn phím. */
function goToSchedule() {
  const el = props.target
  if (!el) return
  el.scrollIntoView({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' })
  el.querySelector('[role="radio"][aria-checked="true"]')?.focus({ preventScroll: true })
}

watch(() => props.target, observe)
onMounted(() => {
  // Chừa chỗ ở cuối trang (CSS chỉ áp dụng dưới 768 px) để thanh không che chân trang
  document.body.classList.add(BODY_CLASS)
  observe(props.target)
})
onBeforeUnmount(() => {
  document.body.classList.remove(BODY_CLASS)
  observer?.disconnect()
})

defineExpose({ visible })
</script>
