<template>
  <img
    v-if="src && !failed"
    :src="src"
    alt=""
    :class="['shrink-0 rounded-full bg-slate-100 object-cover', sizeClass]"
    loading="lazy"
    decoding="async"
    data-test="doctor-avatar-photo"
    @error="failed = true"
  />
  <svg
    v-else
    :class="['shrink-0 rounded-full', sizeClass]"
    viewBox="0 0 80 80"
    role="presentation"
    aria-hidden="true"
    focusable="false"
    data-test="doctor-avatar"
    :data-traits="signature"
  >
    <defs>
      <clipPath :id="clipId"><circle cx="40" cy="40" r="40" /></clipPath>
    </defs>
    <g :clip-path="`url(#${clipId})`">
      <rect width="80" height="80" :fill="t.background" />
      <!-- tóc phía sau (kiểu búi) -->
      <circle v-if="t.hairStyle === 'bun'" cx="40" cy="17" r="8" :fill="t.hairColor" />
      <!-- cổ và thân áo blouse trắng -->
      <rect x="34" y="44" width="12" height="12" :fill="t.skin" />
      <path d="M12 80 C12 62 24 54 40 54 C56 54 68 62 68 80 Z" fill="#ffffff" stroke="#cbd5e1" stroke-width="1" />
      <!-- áo bên trong và ve áo -->
      <path d="M34 54 L40 66 L46 54 Z" fill="#7dd3fc" />
      <path d="M33 54 L40 70 L36 80 M47 54 L40 70 L44 80" fill="none" stroke="#cbd5e1" stroke-width="1.2" />
      <!-- ống nghe -->
      <path d="M31 56 C27 66 32 72 38 71" fill="none" :stroke="t.scope" stroke-width="2.2" stroke-linecap="round" />
      <circle cx="39" cy="71" r="2.6" :fill="t.scope" />
      <!-- đầu -->
      <ellipse cx="40" cy="34" rx="13" ry="15" :fill="t.skin" />
      <!-- tóc -->
      <path v-if="t.hairStyle === 'short'" d="M27 33 C26 20 33 16 40 16 C48 16 55 20 53 33 C50 26 44 24 40 24 C35 24 30 26 27 33 Z" :fill="t.hairColor" />
      <path v-else-if="t.hairStyle === 'side'" d="M26 35 C24 20 34 15 42 16 C51 17 56 23 54 34 C51 27 45 23 36 26 C32 27 28 30 26 35 Z" :fill="t.hairColor" />
      <path v-else d="M27 32 C27 21 33 18 40 18 C47 18 53 21 53 32 C49 25 45 23 40 23 C35 23 31 25 27 32 Z" :fill="t.hairColor" />
      <!-- mắt và miệng -->
      <circle cx="35" cy="35" r="1.4" fill="#1f2937" />
      <circle cx="45" cy="35" r="1.4" fill="#1f2937" />
      <path d="M36 41 Q40 44 44 41" fill="none" stroke="#9a3412" stroke-width="1.3" stroke-linecap="round" />
    </g>
  </svg>
</template>

<script setup>
// Ảnh đại diện minh họa của bác sĩ, vẽ bằng SVG ngay trong trang (không tải ảnh ngoài, giữ CSP).
// Chỉ để trang trí: tên bác sĩ luôn ở ngay cạnh, nên ảnh ẩn khỏi trình đọc màn hình (alt rỗng).
// Có `src` (ảnh do bác sĩ hoặc quản trị viên tải lên) thì hiện ảnh đó; ảnh lỗi thì quay về ảnh minh họa.
import { computed, ref, useId, watch } from 'vue'
import { avatarTraits } from './doctorAvatarTraits'

const props = defineProps({
  doctorId: { type: [Number, String], default: null },
  size: { type: String, default: 'md' },
  src: { type: String, default: null }
})

const failed = ref(false)
watch(() => props.src, () => { failed.value = false })

const clipId = `doctor-avatar-${useId()}`
const t = computed(() => avatarTraits(props.doctorId))
const signature = computed(() => Object.values(t.value).join('|'))
const SIZES = { xs: 'h-10 w-10', sm: 'h-14 w-14', md: 'h-20 w-20', lg: 'h-24 w-24' }
const sizeClass = computed(() => SIZES[props.size] || SIZES.md)
</script>
