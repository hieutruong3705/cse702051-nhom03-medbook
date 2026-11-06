<template>
  <ul v-if="stats.length" ref="root" class="grid grid-cols-2 gap-3 lg:grid-cols-4" aria-label="MedBook qua các con số" data-test="clinic-stats">
    <li v-for="stat in stats" :key="stat.key" class="flex items-center gap-3 rounded-md border border-slate-200 bg-white p-4 shadow-sm">
      <span :class="['flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-lg', stat.tone]" aria-hidden="true">
        <i :class="stat.icon"></i>
      </span>
      <span class="min-w-0">
        <!-- Số đếm lên chỉ để nhìn; trình đọc màn hình đọc giá trị cuối ở thẻ sr-only, không bị đọc từng bước -->
        <span class="block text-2xl font-bold tabular-nums leading-tight text-slate-950" aria-hidden="true" :data-test="`stat-${stat.key}`">{{ stat.display }}</span>
        <span class="sr-only">{{ stat.value }}</span>
        <RouterLink class="text-sm text-slate-700 underline-offset-2 hover:text-primary-700 hover:underline" :to="stat.to">{{ stat.label }}</RouterLink>
      </span>
    </li>
  </ul>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { countUp, motionAllowed } from '@/utils/motion'

const props = defineProps({
  // Kết quả GET /clinic (số liệu thật từ CSDL); null khi chưa tải hoặc lỗi thì không hiện dải số liệu
  clinic: { type: Object, default: null }
})

const COUNTED = ['doctors', 'specialties', 'services']
const root = ref(null)
// Giá trị đang hiện của các số đếm; null nghĩa là hiện thẳng giá trị cuối
const shown = reactive({ doctors: null, specialties: null, services: null })
const cancels = []
let observer = null

const stats = computed(() => {
  const c = props.clinic
  if (!c) return []
  const rows = [
    { key: 'doctors', value: c.doctorCount ?? 0, label: 'bác sĩ đang nhận khám', to: '/doctors', icon: 'fa-solid fa-user-doctor', tone: 'bg-sky-50 text-sky-800' },
    { key: 'specialties', value: c.specialtyCount ?? 0, label: 'chuyên khoa', to: '/specialties', icon: 'fa-solid fa-staff-snake', tone: 'bg-violet-50 text-violet-800' },
    { key: 'services', value: c.serviceCount ?? 0, label: 'dịch vụ khám', to: '/services', icon: 'fa-solid fa-clipboard-list', tone: 'bg-emerald-50 text-emerald-800' },
    {
      key: 'rating',
      value: c.averageRating ? `${Number(c.averageRating).toFixed(1).replace('.', ',')}/5` : 'Chưa có',
      label: `điểm từ ${c.reviewCount ?? 0} đánh giá`,
      to: '/reviews',
      icon: 'fa-solid fa-star',
      tone: 'bg-amber-50 text-amber-800'
    }
  ]
  return rows.map((row) => ({ ...row, display: shown[row.key] ?? row.value }))
})

function startCounting() {
  for (const key of COUNTED) {
    const target = stats.value.find((s) => s.key === key)?.value
    cancels.push(countUp(target, (v) => (shown[key] = v)))
  }
}

// Đếm một lần khi dải số liệu cuộn vào màn hình; không được phép chuyển động thì hiện thẳng giá trị cuối
watch(
  () => props.clinic,
  async (clinic) => {
    if (!clinic || !motionAllowed() || observer) return
    for (const key of COUNTED) shown[key] = 0
    await nextTick()
    if (!root.value) return
    observer = new IntersectionObserver((entries) => {
      if (!entries.some((e) => e.isIntersecting)) return
      observer.disconnect()
      startCounting()
    })
    observer.observe(root.value)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  observer?.disconnect()
  cancels.forEach((cancel) => cancel())
})
</script>
