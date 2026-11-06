<template>
  <PublicLayout>
    <PageHeader title="Chuyên khoa" description="Danh sách chuyên khoa đang hoạt động." />
    <section class="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article v-for="item in specialties" :key="item.id" class="rounded-md border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
          <SpecialtyBadge :specialty="item" variant="icon" size="sm" />
          <h2 class="mt-4 text-lg font-semibold text-slate-950">
            <RouterLink class="hover:text-primary-700 hover:underline" :to="`/specialties/${item.id}`">{{ item.name }}</RouterLink>
          </h2>
          <p class="mt-2 text-sm text-slate-600">{{ item.description || 'Chuyên khoa trong hệ thống MedBook.' }}</p>
        </article>
      </div>
      <EmptyState v-if="!loading && !specialties.length" class="mt-6" title="Chưa có chuyên khoa" />
      <p v-if="error" class="mt-6 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error }}</p>
    </section>
  </PublicLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import SpecialtyBadge from '@/components/ui/SpecialtyBadge.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { catalogApi } from '@/api/catalog'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

const specialties = ref([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    const response = await catalogApi.specialties.list({ status: 'ACTIVE' })
    specialties.value = Array.isArray(response?.content) ? response.content : response || []
  } catch (err) {
    error.value = apiErrorMessage(err)
  } finally {
    loading.value = false
  }
})
</script>
