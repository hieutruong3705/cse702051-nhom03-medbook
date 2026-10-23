import { formatTime } from '@/utils/formatters'

/** Ngày hôm nay theo múi giờ máy người dùng, dạng YYYY-MM-DD (không dùng toISOString vì nó là UTC). */
export function todayIso(now = new Date()) {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

export const isPastDate = (iso) => Boolean(iso) && iso < todayIso()

/** API có thể trả mảng thuần hoặc trang `{ content }`. */
export function normalizeList(response) {
  if (Array.isArray(response)) return response
  return response?.content ?? response?.items ?? []
}

/** Chuẩn hóa slot về { id, date, startTime, endTime } bất kể backend trả `slotDate` hay `date`. */
export function normalizeSlot(raw) {
  return {
    id: raw.id,
    date: raw.slotDate ?? raw.date ?? '',
    startTime: raw.startTime ?? '',
    endTime: raw.endTime ?? ''
  }
}

export const slotLabel = (slot) => [formatTime(slot.startTime), formatTime(slot.endTime)].filter(Boolean).join(' – ')

export const doctorSpecialty = (doctor) => doctor?.specialtyName || doctor?.specialty?.name || ''
export const doctorName = (doctor) => doctor?.fullName || doctor?.name || ''
