// Màu và biểu tượng riêng cho từng chuyên khoa, để khách nhận ra chuyên khoa bằng mắt trước khi đọc chữ.
// Chữ dùng sắc độ 800 trên nền 50 nên đạt tương phản WCAG AA (≥ 4,5:1). Biểu tượng chỉ để trang trí (aria-hidden).
// Lớp Tailwind viết đầy đủ ở đây để trình biên dịch Tailwind quét thấy.

const THEMES = {
  NTQ: { icon: 'fa-stethoscope', soft: 'bg-sky-50', text: 'text-sky-800', ring: 'ring-sky-200', solid: 'bg-sky-700' },
  NK: { icon: 'fa-baby', soft: 'bg-amber-50', text: 'text-amber-800', ring: 'ring-amber-200', solid: 'bg-amber-700' },
  DL: { icon: 'fa-hand-dots', soft: 'bg-rose-50', text: 'text-rose-800', ring: 'ring-rose-200', solid: 'bg-rose-700' },
  TM: { icon: 'fa-heart-pulse', soft: 'bg-red-50', text: 'text-red-800', ring: 'ring-red-200', solid: 'bg-red-700' },
  TMH: { icon: 'fa-ear-listen', soft: 'bg-violet-50', text: 'text-violet-800', ring: 'ring-violet-200', solid: 'bg-violet-700' },
  RHM: { icon: 'fa-tooth', soft: 'bg-teal-50', text: 'text-teal-800', ring: 'ring-teal-200', solid: 'bg-teal-700' },
  MAT: { icon: 'fa-eye', soft: 'bg-indigo-50', text: 'text-indigo-800', ring: 'ring-indigo-200', solid: 'bg-indigo-700' },
  CXK: { icon: 'fa-bone', soft: 'bg-orange-50', text: 'text-orange-800', ring: 'ring-orange-200', solid: 'bg-orange-700' },
  TK: { icon: 'fa-brain', soft: 'bg-fuchsia-50', text: 'text-fuchsia-800', ring: 'ring-fuchsia-200', solid: 'bg-fuchsia-700' },
  SPK: { icon: 'fa-person-pregnant', soft: 'bg-pink-50', text: 'text-pink-800', ring: 'ring-pink-200', solid: 'bg-pink-700' }
}

export const DEFAULT_THEME = Object.freeze({
  icon: 'fa-staff-snake',
  soft: 'bg-primary-50',
  text: 'text-primary-700',
  ring: 'ring-primary-100',
  solid: 'bg-primary-700'
})

// API công khai của bác sĩ chỉ trả tên chuyên khoa, nên cần tra ngược tên → mã
const NAME_TO_CODE = {
  'noi tong quat': 'NTQ',
  'nhi khoa': 'NK',
  'da lieu': 'DL',
  'tim mach': 'TM',
  'tai mui hong': 'TMH',
  'rang ham mat': 'RHM',
  'mat': 'MAT',
  'nhan khoa': 'MAT',
  'co xuong khop': 'CXK',
  'than kinh': 'TK',
  'san phu khoa': 'SPK'
}

export function normalizeName(name) {
  return String(name || '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * Trả chủ đề của một chuyên khoa. Nhận đối tượng chuyên khoa ({code, name}), bác sĩ ({specialtyName})
 * hoặc chuỗi mã/tên. Mã hoặc tên lạ dùng chủ đề mặc định.
 */
export function specialtyTheme(input) {
  if (!input) return DEFAULT_THEME
  const code = typeof input === 'string' ? input : input.code || input.specialtyCode
  const name = typeof input === 'string' ? input : input.name || input.specialtyName
  const byCode = code && THEMES[String(code).trim().toUpperCase()]
  if (byCode) return byCode
  const mapped = NAME_TO_CODE[normalizeName(name)]
  return (mapped && THEMES[mapped]) || DEFAULT_THEME
}
