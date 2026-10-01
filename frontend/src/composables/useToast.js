import { readonly, ref } from 'vue'

const toasts = ref([])
let nextId = 1

export function useToast() {
  const remove = (id) => {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  const show = ({ title, message, type = 'info', timeout = 4000 }) => {
    const id = nextId++
    toasts.value.push({ id, title, message, type })
    if (timeout > 0) {
      window.setTimeout(() => remove(id), timeout)
    }
    return id
  }

  return {
    toasts: readonly(toasts),
    show,
    success: (message, title = 'Thành công') => show({ title, message, type: 'success' }),
    error: (message, title = 'Có lỗi xảy ra') => show({ title, message, type: 'error', timeout: 6000 }),
    info: (message, title = 'Thông tin') => show({ title, message, type: 'info' }),
    warning: (message, title = 'Cần chú ý') => show({ title, message, type: 'warning', timeout: 6000 }),
    remove
  }
}
