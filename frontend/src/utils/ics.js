// Tạo tệp lịch iCalendar (RFC 5545) cho một lịch hẹn ngay trên trình duyệt, không gọi máy chủ.
// Giờ khám là giờ Việt Nam (phòng khám mô phỏng ở Hà Nội), ghi kèm TZID để ứng dụng lịch hiện đúng giờ địa phương.

export const TZID = 'Asia/Ho_Chi_Minh'

// Ký tự đặc biệt trong TEXT phải thoát: \ ; , và xuống dòng (RFC 5545 mục 3.3.11)
export function escapeText(value) {
  return String(value ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r?\n/g, '\\n')
}

// Gập dòng dài hơn 75 octet (UTF-8), dòng tiếp theo bắt đầu bằng một dấu cách; không cắt giữa một ký tự
export function foldLine(line) {
  const encoder = new TextEncoder()
  const parts = []
  let current = ''
  let bytes = 0
  for (const ch of line) {
    const size = encoder.encode(ch).length
    const limit = parts.length ? 74 : 75
    if (bytes + size > limit) {
      parts.push(current)
      current = ''
      bytes = 0
    }
    current += ch
    bytes += size
  }
  parts.push(current)
  return parts.join('\r\n ')
}

const compactDate = (date) => String(date).replace(/-/g, '')
const compactTime = (time) => String(time).slice(0, 8).replace(/:/g, '').padEnd(6, '0')
const utcStamp = (now) => now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')

/**
 * @param {{ id, title, description?, location?, date: 'YYYY-MM-DD', startTime: 'HH:MM[:SS]', endTime: 'HH:MM[:SS]', now?: Date }} event
 */
export function buildIcs({ id, title, description = '', location = '', date, startTime, endTime, now = new Date() }) {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//MedBook Nhom 03//Dat lich kham//VI',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VTIMEZONE',
    `TZID:${TZID}`,
    'BEGIN:STANDARD',
    'DTSTART:19700101T000000',
    'TZOFFSETFROM:+0700',
    'TZOFFSETTO:+0700',
    'TZNAME:ICT',
    'END:STANDARD',
    'END:VTIMEZONE',
    'BEGIN:VEVENT',
    `UID:appointment-${id}@medbook.local`,
    `DTSTAMP:${utcStamp(now)}`,
    `DTSTART;TZID=${TZID}:${compactDate(date)}T${compactTime(startTime)}`,
    `DTEND;TZID=${TZID}:${compactDate(date)}T${compactTime(endTime)}`,
    `SUMMARY:${escapeText(title)}`,
    ...(description ? [`DESCRIPTION:${escapeText(description)}`] : []),
    ...(location ? [`LOCATION:${escapeText(location)}`] : []),
    'BEGIN:VALARM',
    'TRIGGER:-PT1H',
    'ACTION:DISPLAY',
    `DESCRIPTION:${escapeText(title)}`,
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR'
  ]
  return lines.map(foldLine).join('\r\n') + '\r\n'
}

/** Cho người dùng tải tệp .ics (Blob trong trình duyệt, không tải tài nguyên ngoài). */
export function downloadIcs(filename, content) {
  const blob = new Blob([content], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
