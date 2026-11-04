<template>
  <!-- Thẻ bác sĩ kiểu "hồ sơ bên trái, lịch khám bên phải": khách xem và chọn khung giờ ngay trong danh sách -->
  <article class="grid grid-cols-1 gap-5 rounded-md border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md md:grid-cols-2" data-test="doctor-card">
    <div class="flex gap-4">
      <DoctorAvatar :doctor-id="doctor.id" />
      <div class="min-w-0">
        <component :is="headingTag" class="text-lg font-semibold text-primary-800">
          <RouterLink class="hover:underline" :to="`/doctors/${doctor.id}`">{{ doctor.fullName }}</RouterLink>
        </component>
        <p v-if="doctor.academicTitle" class="text-sm font-medium text-slate-800">{{ doctor.academicTitle }}</p>
        <div class="mt-1 flex items-center gap-2 text-sm text-slate-700">
          <StarRating :value="doctor.averageRating" show-value />
          <span>{{ doctor.reviewCount || 0 }} đánh giá</span>
        </div>
        <ul class="mt-2 flex flex-wrap gap-1.5" aria-label="Thông tin nổi bật" data-test="doctor-badges">
          <li><SpecialtyBadge :specialty="doctor.specialtyName ? doctor : null" /></li>
          <li v-for="badge in badges" :key="badge.key">
            <span
              :class="['inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset', badge.tone]"
              :data-test="`badge-${badge.key}`"
            >
              <i :class="badge.icon" aria-hidden="true"></i>{{ badge.label }}
            </span>
          </li>
        </ul>
        <p class="mt-2 line-clamp-3 text-sm text-slate-700">{{ doctor.expertise || doctor.bio || 'Bác sĩ chưa cập nhật phần giới thiệu.' }}</p>
        <RouterLink class="mt-2 inline-block text-sm font-semibold text-primary-700 underline" :to="`/doctors/${doctor.id}`" :aria-label="`Xem thêm về ${doctor.fullName}`">
          Xem thêm
        </RouterLink>
      </div>
    </div>
    <div class="min-w-0 border-slate-200 md:border-l md:pl-5">
      <DoctorSchedulePicker :doctor-id="doctor.id" :max-slots="8" @availability="hasSlotsToday = $event.today" />
    </div>
  </article>
</template>

<script setup>
import { computed, ref } from 'vue'
import StarRating from '@/components/reviews/StarRating.vue'
import SpecialtyBadge from '@/components/ui/SpecialtyBadge.vue'
import DoctorAvatar from './DoctorAvatar.vue'
import DoctorSchedulePicker from './DoctorSchedulePicker.vue'
import { doctorBadges } from './doctorBadges'

const props = defineProps({
  doctor: { type: Object, required: true },
  // Cấp tiêu đề theo trang chứa thẻ, để thứ bậc tiêu đề của trang không bị nhảy cấp
  headingTag: { type: String, default: 'h2' }
})

const hasSlotsToday = ref(false)
const badges = computed(() => doctorBadges(props.doctor, { hasSlotsToday: hasSlotsToday.value }))
</script>
