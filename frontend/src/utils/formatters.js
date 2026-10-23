const dateFormatter = new Intl.DateTimeFormat('vi-VN')
const dateTimeFormatter = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short',
  timeStyle: 'short'
})
const timeFormatter = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit'
})
const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0
})

const toDate = (value) => (value ? new Date(value) : null)

export const formatDate = (value) => {
  const date = toDate(value)
  return date && !Number.isNaN(date.valueOf()) ? dateFormatter.format(date) : ''
}

export const formatDateTime = (value) => {
  const date = toDate(value)
  return date && !Number.isNaN(date.valueOf()) ? dateTimeFormatter.format(date) : ''
}

export const formatTime = (value) => {
  if (!value) return ''
  if (/^\d{2}:\d{2}/.test(value)) return value.slice(0, 5)
  const date = toDate(value)
  return date && !Number.isNaN(date.valueOf()) ? timeFormatter.format(date) : ''
}

export const formatCurrency = (value) => currencyFormatter.format(Number(value || 0))
