/**
 * Quy tắc dùng chung cho màn hình khám của bác sĩ. Giới hạn độ dài khớp `UpdateEncounterRequest` ở backend.
 */

/** Nội dung của lần khám (PUT /encounters/{id}). */
export const ENCOUNTER_FIELDS = [
  { key: 'chiefComplaint', label: 'Lý do khám', max: 5000, rows: 3 },
  { key: 'diagnosis', label: 'Chẩn đoán', max: 5000, rows: 3 },
  { key: 'clinicalNotes', label: 'Ghi chú lâm sàng', max: 10000, rows: 5 },
  { key: 'treatmentPlan', label: 'Kế hoạch điều trị', max: 5000, rows: 3 },
  { key: 'followUpNote', label: 'Ghi chú tái khám', max: 2000, rows: 2 }
]

/** Tóm tắt bệnh án cập nhật từ lần khám (`clinicalSummary`). Nhóm máu có ô chọn riêng. */
export const SUMMARY_FIELDS = [
  { key: 'chronicConditions', label: 'Bệnh mạn tính', max: 2000, rows: 2 },
  { key: 'allergyNotes', label: 'Dị ứng', max: 2000, rows: 2 },
  { key: 'medicalHistory', label: 'Tiền sử bệnh', max: 5000, rows: 3 },
  { key: 'currentMedications', label: 'Thuốc đang dùng', max: 2000, rows: 2 }
]

export const BLOOD_TYPES = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']

export const BLOOD_TYPE_OPTIONS = [
  { label: 'Chưa rõ', value: '' },
  ...BLOOD_TYPES.map((value) => ({ label: value, value }))
]

const SUMMARY_KEYS = ['bloodType', ...SUMMARY_FIELDS.map((field) => field.key)]
const ENCOUNTER_KEYS = ENCOUNTER_FIELDS.map((field) => field.key)

const text = (value) => (value == null ? '' : String(value))

/** Giá trị ban đầu của biểu mẫu từ lần khám và (nếu có) bệnh án. Mọi trường là chuỗi ('' khi trống). */
export function toFormValues(encounter, record) {
  const values = {}
  ENCOUNTER_KEYS.forEach((key) => {
    values[key] = text(encounter?.[key])
  })
  SUMMARY_KEYS.forEach((key) => {
    values[key] = text(record?.[key])
  })
  return values
}

/**
 * Chỉ gửi những trường ĐÃ ĐỔI so với bản đã lưu (backend hiểu `null`/vắng mặt là giữ nguyên, chuỗi rỗng là xóa).
 * Nhóm máu rỗng không gửi vì không hợp lệ với mẫu A/B/AB/O; ô tóm tắt chỉ gửi khi có ít nhất một thay đổi.
 */
export function buildChanges(values, baseline) {
  const payload = {}
  ENCOUNTER_KEYS.forEach((key) => {
    if (values[key] !== baseline[key]) payload[key] = values[key]
  })

  const summary = {}
  SUMMARY_KEYS.forEach((key) => {
    if (values[key] === baseline[key]) return
    if (key === 'bloodType' && !values[key]) return
    summary[key] = values[key]
  })
  if (Object.keys(summary).length) payload.clinicalSummary = summary
  return payload
}

/** Lỗi độ dài theo từng trường: { [key]: 'thông báo' }. */
export function validateLengths(values) {
  const errors = {}
  ;[...ENCOUNTER_FIELDS, ...SUMMARY_FIELDS].forEach((field) => {
    if (values[field.key]?.length > field.max) {
      errors[field.key] = `${field.label} tối đa ${field.max} ký tự`
    }
  })
  return errors
}

/** Lỗi theo trường từ phản hồi 400 của server (`details`), bỏ tiền tố `clinicalSummary.`. */
export function serverFieldErrors(details = {}) {
  const errors = {}
  Object.entries(details).forEach(([key, message]) => {
    errors[key.replace(/^clinicalSummary\./, '')] = message
  })
  return errors
}

/** Lưu một Blob thành tệp ở máy người dùng (không để URL tạm tồn tại lâu). */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'tep-dinh-kem'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function formatFileSize(bytes) {
  const size = Number(bytes)
  if (!Number.isFinite(size) || size < 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
