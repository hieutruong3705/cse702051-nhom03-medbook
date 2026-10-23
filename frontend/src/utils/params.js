// Bỏ các tham số rỗng trước khi gửi: máy chủ coi chuỗi rỗng là giá trị sai (ví dụ ?status= → 400).
export function cleanParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
}

// Lỗi kiểm tra dữ liệu của máy chủ theo từng trường: { tênTrường: thông điệp }.
export function fieldErrors(error) {
  const details = error?.details
  return details && typeof details === 'object' && !Array.isArray(details) ? details : {}
}

// Tải một Blob về máy với tên tệp cho trước.
export function downloadBlob(data, filename) {
  const url = URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
