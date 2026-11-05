<template>
  <FormField :for-id="id" :label="label" :hint="hintText" :error="error || localError">
    <template #default="{ describedBy, invalid }">
      <div class="flex items-center gap-4" data-test="image-picker">
        <div :class="['flex shrink-0 items-center justify-center overflow-hidden border border-slate-200 bg-slate-50', shapeClass]">
          <img v-if="previewUrl" :src="previewUrl" alt="" class="h-full w-full object-cover" data-test="image-preview" />
          <slot v-else name="placeholder">
            <i class="fa-regular fa-image text-2xl text-slate-400" aria-hidden="true"></i>
          </slot>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <label
            :for="id"
            class="inline-flex cursor-pointer items-center gap-2 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-800 shadow-sm hover:bg-slate-50 focus-within:ring-2 focus-within:ring-primary-600"
          >
            <i class="fa-solid fa-upload" aria-hidden="true"></i>{{ previewUrl ? 'Đổi ảnh' : 'Chọn ảnh' }}
            <input
              :id="id"
              ref="input"
              type="file"
              class="sr-only"
              :accept="ACCEPT"
              :disabled="disabled"
              :aria-describedby="describedBy"
              :aria-invalid="invalid"
              data-test="image-input"
              @change="onChange"
            />
          </label>
          <button
            v-if="previewUrl"
            type="button"
            class="rounded-md px-3 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-600"
            :disabled="disabled"
            data-test="image-remove"
            @click="clear"
          >
            Gỡ ảnh
          </button>
        </div>
      </div>
    </template>
  </FormField>
</template>

<script setup>
// Chọn một ảnh (JPEG, PNG, WebP, tối đa 1 MB) và xem trước trước khi lưu. Không tự gửi lên máy chủ: nơi dùng đọc
// `modelValue` (tệp mới) và `removed` (người dùng bấm "Gỡ ảnh") khi bấm Lưu. Máy chủ vẫn tự kiểm loại và dung lượng.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import FormField from './FormField.vue'

const ACCEPT = 'image/jpeg,image/png,image/webp'
const MAX_BYTES = 1024 * 1024

const props = defineProps({
  id: { type: String, required: true },
  label: { type: String, default: 'Ảnh' },
  hint: { type: String, default: '' },
  error: { type: String, default: '' },
  modelValue: { type: Object, default: null },
  // Ảnh đang có trên máy chủ (đường dẫn), hiện khi chưa chọn ảnh mới và chưa bấm "Gỡ ảnh"
  currentUrl: { type: String, default: null },
  removed: { type: Boolean, default: false },
  // 'circle' cho ảnh đại diện, 'rounded' cho ảnh chuyên khoa
  shape: { type: String, default: 'circle' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'update:removed'])

const input = ref(null)
const localError = ref('')
const objectUrl = ref(null)

const hintText = computed(() => props.hint || 'JPEG, PNG hoặc WebP, tối đa 1 MB.')
const shapeClass = computed(() => (props.shape === 'rounded' ? 'h-16 w-24 rounded-md' : 'h-16 w-16 rounded-full'))
const previewUrl = computed(() => objectUrl.value || (props.removed ? null : props.currentUrl))

function revoke() {
  if (objectUrl.value && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
}

watch(() => props.modelValue, (file) => {
  revoke()
  if (file && typeof URL.createObjectURL === 'function') objectUrl.value = URL.createObjectURL(file)
})

function onChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  localError.value = ''
  if (!file) return
  if (!ACCEPT.split(',').includes(file.type)) {
    localError.value = 'Chỉ nhận ảnh JPEG, PNG hoặc WebP.'
    return
  }
  if (file.size > MAX_BYTES) {
    localError.value = 'Ảnh vượt quá 1 MB, hãy chọn ảnh nhỏ hơn.'
    return
  }
  emit('update:modelValue', file)
  emit('update:removed', false)
}

function clear() {
  localError.value = ''
  emit('update:modelValue', null)
  emit('update:removed', Boolean(props.currentUrl))
}

onBeforeUnmount(revoke)
</script>
