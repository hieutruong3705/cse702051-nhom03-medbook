<template>
  <div :class="['rounded-md border border-dashed border-slate-300 bg-white text-center', compact ? 'px-4 py-5' : 'px-6 py-10']" data-test="empty-state">
    <!-- Hình minh họa tự vẽ (SVG trong trang, chỉ để trang trí) -->
    <svg
      v-if="illustration"
      :class="['mx-auto', compact ? 'h-16 w-20' : 'h-24 w-32']"
      viewBox="0 0 128 96"
      aria-hidden="true"
      focusable="false"
      :data-illustration="illustration"
    >
      <ellipse cx="64" cy="86" rx="44" ry="5" fill="#e2e8f0" />
      <template v-if="illustration === 'reviews'">
        <rect x="22" y="14" width="70" height="46" rx="10" fill="#ecfeff" stroke="#0e7490" stroke-width="2.5" />
        <path d="M38 60 L34 74 L52 60" fill="#ecfeff" stroke="#0e7490" stroke-width="2.5" stroke-linejoin="round" />
        <path v-for="x in [36, 52, 68]" :key="x" :d="star(x + 6, 37)" fill="#f59e0b" />
        <rect x="70" y="40" width="36" height="28" rx="8" fill="#fff7ed" stroke="#c2410c" stroke-width="2.5" />
        <circle cx="80" cy="54" r="2.5" fill="#c2410c" /><circle cx="88" cy="54" r="2.5" fill="#c2410c" /><circle cx="96" cy="54" r="2.5" fill="#c2410c" />
      </template>
      <template v-else-if="illustration === 'calendar'">
        <rect x="30" y="18" width="68" height="60" rx="8" fill="#ffffff" stroke="#0e7490" stroke-width="2.5" />
        <path d="M30 34 H98" stroke="#0e7490" stroke-width="2.5" />
        <rect x="30" y="18" width="68" height="16" rx="8" fill="#cffafe" />
        <path d="M46 12 V24 M82 12 V24" stroke="#0e7490" stroke-width="3" stroke-linecap="round" />
        <g fill="#e2e8f0"><rect x="40" y="42" width="10" height="8" rx="2" /><rect x="59" y="42" width="10" height="8" rx="2" /><rect x="78" y="42" width="10" height="8" rx="2" /><rect x="40" y="58" width="10" height="8" rx="2" /></g>
        <circle cx="74" cy="62" r="10" fill="#fff1f2" stroke="#be123c" stroke-width="2.5" />
        <path d="M70 58 L78 66 M78 58 L70 66" stroke="#be123c" stroke-width="2.5" stroke-linecap="round" />
      </template>
      <template v-else>
        <rect x="24" y="22" width="50" height="58" rx="6" fill="#ffffff" stroke="#94a3b8" stroke-width="2.5" />
        <path d="M34 36 H64 M34 46 H58 M34 56 H50" stroke="#cbd5e1" stroke-width="3" stroke-linecap="round" />
        <circle cx="78" cy="48" r="16" fill="#ecfeff" stroke="#0e7490" stroke-width="3" />
        <path d="M90 60 L104 74" stroke="#0e7490" stroke-width="5" stroke-linecap="round" />
        <path d="M72 44 Q78 38 84 44" fill="none" stroke="#0e7490" stroke-width="2" stroke-linecap="round" />
      </template>
    </svg>
    <i v-else-if="icon" :class="icon" class="text-3xl text-slate-400" aria-hidden="true"></i>
    <component :is="headingTag" :class="['font-semibold text-slate-900', compact ? 'mt-2 text-sm' : 'mt-3 text-base']">{{ title }}</component>
    <p v-if="message" class="mx-auto mt-1 max-w-md text-sm text-slate-600">{{ message }}</p>
    <div v-if="$slots.default" :class="compact ? 'mt-3' : 'mt-5'">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  icon: { type: String, default: 'fa-regular fa-folder-open' },
  title: { type: String, required: true },
  message: { type: String, default: '' },
  // 'reviews' | 'calendar' | 'search': thay biểu tượng bằng hình minh họa
  illustration: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  // Cấp tiêu đề theo trang chứa (mặc định h2 như trước); 'p' khi trạng thái rỗng nằm trong một khối nhỏ
  headingTag: { type: String, default: 'h2' }
})

// Ngôi sao 5 cánh tâm (cx, cy), bán kính ngoài 6
function star(cx, cy) {
  const points = Array.from({ length: 10 }, (_, i) => {
    const r = i % 2 ? 2.6 : 6
    const a = (Math.PI / 5) * i - Math.PI / 2
    return `${(cx + r * Math.cos(a)).toFixed(1)},${(cy + r * Math.sin(a)).toFixed(1)}`
  })
  return `M${points.join(' L')} Z`
}
</script>
