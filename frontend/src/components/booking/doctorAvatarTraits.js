// Chọn đặc điểm ảnh minh họa bác sĩ theo mã bác sĩ: cùng mã luôn ra cùng ảnh.
// Ảnh là hình vẽ tự tạo, không phải ảnh người thật.

export const BACKGROUNDS = ['#cffafe', '#e0e7ff', '#fce7f3', '#dcfce7', '#fef3c7', '#ede9fe']
export const SKINS = ['#f5d0b5', '#e8b796', '#c98f6b', '#a86f4f']
export const HAIR_COLORS = ['#1f2937', '#3f2a1d', '#6b4423', '#9ca3af']
export const HAIR_STYLES = ['short', 'side', 'bun']
// Màu ống nghe: đậm, nổi trên áo blouse trắng
export const SCOPES = ['#0e7490', '#4338ca', '#be123c']

const COMBOS = BACKGROUNDS.length * SKINS.length * HAIR_COLORS.length * HAIR_STYLES.length * SCOPES.length // 864
// Bước nhảy nguyên tố cùng nhau với COMBOS: mã 1..864 cho 864 ảnh khác nhau, các mã liền nhau trông khác hẳn nhau
const STRIDE = 389

function seedOf(id) {
  const n = Number(id)
  if (Number.isInteger(n) && n >= 0) return n
  // Mã không phải số: băm chuỗi (FNV-1a)
  let h = 2166136261
  for (const ch of String(id ?? '')) {
    h ^= ch.codePointAt(0)
    h = Math.imul(h, 16777619) >>> 0
  }
  return h
}

export function avatarTraits(id) {
  let k = (seedOf(id) * STRIDE) % COMBOS
  const pick = (list) => {
    const value = list[k % list.length]
    k = Math.floor(k / list.length)
    return value
  }
  return {
    background: pick(BACKGROUNDS),
    skin: pick(SKINS),
    hairColor: pick(HAIR_COLORS),
    hairStyle: pick(HAIR_STYLES),
    scope: pick(SCOPES)
  }
}
