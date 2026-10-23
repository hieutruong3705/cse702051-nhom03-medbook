import { reactive, ref } from 'vue'

export function useForm(initialValues = {}, validators = {}) {
  const values = reactive({ ...initialValues })
  const errors = reactive({})
  const submitting = ref(false)

  const clearErrors = () => {
    Object.keys(errors).forEach((key) => delete errors[key])
  }

  const validate = () => {
    clearErrors()
    for (const [field, validator] of Object.entries(validators)) {
      const message = validator(values[field], values)
      if (message) errors[field] = message
    }
    return Object.keys(errors).length === 0
  }

  const applyApiErrors = (apiError) => {
    clearErrors()
    Object.entries(apiError?.details || {}).forEach(([field, message]) => {
      errors[field] = Array.isArray(message) ? message.join(', ') : String(message)
    })
  }

  const reset = (nextValues = initialValues) => {
    clearErrors()
    Object.keys(values).forEach((key) => delete values[key])
    Object.assign(values, nextValues)
  }

  return { values, errors, submitting, validate, clearErrors, applyApiErrors, reset }
}
