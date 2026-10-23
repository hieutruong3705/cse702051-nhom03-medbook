import { computed, ref } from 'vue'

export function usePagination(initial = {}) {
  const page = ref(initial.page ?? 0)
  const size = ref(initial.size ?? 10)
  const totalElements = ref(initial.totalElements ?? 0)

  const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / size.value)))
  const setPageResponse = (response) => {
    page.value = response?.page ?? page.value
    size.value = response?.size ?? size.value
    totalElements.value = response?.totalElements ?? totalElements.value
  }
  const reset = () => {
    page.value = 0
    totalElements.value = 0
  }

  return { page, size, totalElements, totalPages, setPageResponse, reset }
}
