import { formatTime } from '@/utils/formatters'

// Đường dẫn tới trang đặt lịch có sẵn bác sĩ và khung giờ đã chọn. Trả chuỗi (không phải đối tượng route)
// để dùng được cả trong RouterLink lẫn thẻ <a> và dễ kiểm trong test.
export function bookingLink(doctorId, slot, date) {
  const query = new URLSearchParams({
    doctorId: String(doctorId),
    slotId: String(slot.id),
    date,
    start: formatTime(slot.startTime),
    end: formatTime(slot.endTime)
  })
  return `/patient/booking?${query.toString().replace(/%3A/g, ':')}`
}

const WEEKDAYS = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7']
const iso = (day) => `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`

// count ngày tính từ hôm nay theo giờ máy người dùng: { value: 'YYYY-MM-DD', weekday, dm, label }
export function upcomingDays(count = 7, now = new Date()) {
  return Array.from({ length: count }, (_, i) => {
    const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i)
    const dm = `${day.getDate()}/${day.getMonth() + 1}`
    const weekday = i === 0 ? 'Hôm nay' : WEEKDAYS[day.getDay()]
    return { value: iso(day), weekday, dm, label: `${weekday} - ${dm}` }
  })
}

/** Ghép giờ trống của nhiều bác sĩ ([{ doctor, slots }]), sắp theo giờ bắt đầu, lấy limit giờ sớm nhất. */
export function earliestSlots(perDoctor, limit = 5) {
  return perDoctor
    .flatMap(({ doctor, slots }) => (Array.isArray(slots) ? slots : []).map((slot) => ({ doctor, slot })))
    .sort((a, b) => String(a.slot.startTime).localeCompare(String(b.slot.startTime)) || a.doctor.id - b.doctor.id)
    .slice(0, limit)
}
