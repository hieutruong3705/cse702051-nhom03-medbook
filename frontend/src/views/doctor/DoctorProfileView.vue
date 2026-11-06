<template>
  <DoctorLayout>
    <PageHeader title="Hồ sơ bác sĩ" eyebrow="Bác sĩ" description="Cập nhật phần giới thiệu hiện trên trang công khai của bạn.">
      <template v-if="doctor.id" #actions>
        <RouterLink class="inline-flex items-center gap-2 text-sm font-semibold text-primary-700 hover:underline" :to="`/doctors/${doctor.id}`">
          <i class="fa-regular fa-eye" aria-hidden="true"></i>Xem trang công khai
        </RouterLink>
      </template>
    </PageHeader>

    <section class="grid gap-4 lg:grid-cols-[1fr_2fr]">
      <div class="space-y-4">
        <article class="rounded-md border border-slate-200 bg-white p-5">
          <div class="flex items-center gap-3">
            <DoctorAvatar v-if="doctor.id" :doctor-id="doctor.id" :src="doctor.avatarUrl" size="sm" />
            <h2 class="font-semibold text-slate-950">Thông tin tài khoản</h2>
          </div>
          <dl class="mt-4 space-y-3 text-sm">
            <div><dt class="text-slate-600">Họ tên</dt><dd class="font-medium">{{ user.fullName || '-' }}</dd></div>
            <div><dt class="text-slate-600">Email</dt><dd>{{ user.email || '-' }}</dd></div>
            <div><dt class="text-slate-600">Điện thoại</dt><dd>{{ user.phone || '-' }}</dd></div>
          </dl>
          <RouterLink class="mt-4 inline-block text-sm font-semibold text-primary-700 hover:underline" to="/account/profile">Sửa tài khoản</RouterLink>
        </article>

        <form class="rounded-md border border-slate-200 bg-white p-5" novalidate data-test="doctor-avatar-form" @submit.prevent="saveAvatar">
          <h2 class="font-semibold text-slate-950">Ảnh đại diện</h2>
          <p class="mt-1 text-xs text-slate-600">Hiện trên trang công khai và thẻ bác sĩ. Không có ảnh thì dùng ảnh minh họa.</p>
          <div class="mt-4">
            <ImagePicker id="doctor-avatar" v-model="avatarFile" v-model:removed="avatarRemoved" label="Chọn ảnh" :current-url="doctor.avatarUrl || null">
              <template #placeholder><DoctorAvatar v-if="doctor.id" :doctor-id="doctor.id" size="sm" /></template>
            </ImagePicker>
          </div>
          <p v-if="avatarError" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ avatarError }}</p>
          <p v-if="avatarMessage" class="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">{{ avatarMessage }}</p>
          <div class="mt-4 flex justify-end">
            <BaseButton type="submit" variant="secondary" :loading="avatarSaving" :disabled="!avatarFile && !avatarRemoved" icon="fa-solid fa-image">Lưu ảnh</BaseButton>
          </div>
        </form>

        <article class="rounded-md border border-slate-200 bg-white p-5">
          <h2 class="font-semibold text-slate-950">Thông tin do quản trị viên quản lý</h2>
          <dl class="mt-4 space-y-3 text-sm">
            <div><dt class="text-slate-600">Chuyên khoa</dt><dd class="font-medium">{{ doctor.specialtyName || '-' }}</dd></div>
            <div><dt class="text-slate-600">Số giấy phép</dt><dd>{{ doctor.licenseNumber || '-' }}</dd></div>
          </dl>
          <p class="mt-3 text-xs text-slate-600">Liên hệ quản trị viên nếu cần đổi chuyên khoa hoặc số giấy phép hành nghề.</p>
        </article>
      </div>

      <form class="rounded-md border border-slate-200 bg-white p-5" novalidate data-test="doctor-profile-form" @submit.prevent="submit">
        <h2 class="font-semibold text-slate-950">Giới thiệu trên trang công khai</h2>
        <p v-if="loadError" class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ loadError }}</p>
        <div class="mt-4 grid gap-4 md:grid-cols-2">
          <BaseInput id="doctor-title" v-model="form.academicTitle" label="Học hàm, học vị" placeholder="Ví dụ: Thạc sĩ, Bác sĩ chuyên khoa I" :error="errors.academicTitle" />
          <BaseInput id="doctor-experience" v-model="form.yearsOfExperience" type="number" label="Số năm kinh nghiệm" :error="errors.yearsOfExperience" />
          <div class="md:col-span-2">
            <BaseTextarea id="doctor-bio" v-model="form.bio" label="Giới thiệu ngắn" :rows="3" :error="errors.bio" />
          </div>
          <div class="md:col-span-2">
            <BaseTextarea id="doctor-expertise" v-model="form.expertise" label="Thế mạnh chuyên môn" :rows="3" :error="errors.expertise" />
          </div>
          <BaseTextarea id="doctor-education" v-model="form.education" label="Quá trình đào tạo" :rows="4" :error="errors.education" />
          <BaseTextarea id="doctor-work-history" v-model="form.workHistory" label="Quá trình công tác" :rows="4" :error="errors.workHistory" />
        </div>
        <p v-if="error" class="mt-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ error }}</p>
        <p v-if="successMessage" class="mt-4 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700" role="status">{{ successMessage }}</p>
        <div class="mt-5 flex justify-end">
          <BaseButton type="submit" :loading="saving" icon="fa-solid fa-floppy-disk">Lưu hồ sơ</BaseButton>
        </div>
      </form>
    </section>
  </DoctorLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BaseButton, BaseInput, BaseTextarea, ImagePicker, PageHeader } from '@/components/ui'
import DoctorLayout from '@/layouts/DoctorLayout.vue'
import DoctorAvatar from '@/components/booking/DoctorAvatar.vue'
import { doctorsApi } from '@/api/doctors'
import { usersApi } from '@/api/users'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { fieldErrors } from '@/utils/params'

const FIELDS = ['bio', 'academicTitle', 'yearsOfExperience', 'education', 'expertise', 'workHistory']

const user = ref({})
const doctor = ref({})
const form = reactive(Object.fromEntries(FIELDS.map((field) => [field, ''])))
const errors = reactive({})
const loadError = ref('')
const error = ref('')
const successMessage = ref('')
const saving = ref(false)
const avatarFile = ref(null)
const avatarRemoved = ref(false)
const avatarSaving = ref(false)
const avatarError = ref('')
const avatarMessage = ref('')

function fill(profile) {
  doctor.value = profile
  for (const field of FIELDS) form[field] = profile[field] ?? ''
}

onMounted(async () => {
  const [account, profile] = await Promise.allSettled([usersApi.me(), doctorsApi.myProfile()])
  if (account.status === 'fulfilled') user.value = account.value
  if (profile.status === 'fulfilled') fill(profile.value)
  else loadError.value = apiErrorMessage(profile.reason)
})

async function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  successMessage.value = ''
  Object.keys(errors).forEach((key) => delete errors[key])
  try {
    const payload = { ...form, yearsOfExperience: form.yearsOfExperience === '' ? null : Number(form.yearsOfExperience) }
    fill(await doctorsApi.updateMyProfile(payload))
    successMessage.value = 'Đã lưu hồ sơ. Trang công khai hiển thị thông tin mới ngay.'
  } catch (err) {
    Object.assign(errors, fieldErrors(err))
    error.value = apiErrorMessage(err)
  } finally {
    saving.value = false
  }
}

async function saveAvatar() {
  if (avatarSaving.value || (!avatarFile.value && !avatarRemoved.value)) return
  avatarSaving.value = true
  avatarError.value = ''
  avatarMessage.value = ''
  try {
    const profile = avatarFile.value ? await doctorsApi.uploadMyAvatar(avatarFile.value) : await doctorsApi.removeMyAvatar()
    doctor.value = profile
    avatarFile.value = null
    avatarRemoved.value = false
    avatarMessage.value = profile.avatarUrl ? 'Đã lưu ảnh đại diện.' : 'Đã gỡ ảnh, trang công khai dùng ảnh minh họa.'
  } catch (err) {
    avatarError.value = apiErrorMessage(err)
  } finally {
    avatarSaving.value = false
  }
}
</script>
