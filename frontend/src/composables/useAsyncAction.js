import { ref } from 'vue'
import { apiErrorMessage } from '@/utils/apiErrorMessage'

export function useAsyncAction(action, options = {}) {
  const loading = ref(false)
  const error = ref('')

  const run = async (...args) => {
    if (loading.value) return undefined
    loading.value = true
    error.value = ''
    try {
      return await action(...args)
    } catch (err) {
      error.value = apiErrorMessage(err)
      options.onError?.(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  return { loading, error, run }
}
