// Huy hiệu trên thẻ bác sĩ, tính từ dữ liệu công khai đã có (không cần API mới).
// Màu chữ 800 trên nền 50 đạt tương phản WCAG AA.

export const MANY_REVIEWS = 3
export const TOP_RATING = 4.5

export function doctorBadges(doctor, { hasSlotsToday = false } = {}) {
  if (!doctor) return []
  const badges = []
  if (hasSlotsToday) {
    badges.push({ key: 'today', label: 'Còn lịch hôm nay', icon: 'fa-solid fa-bolt', tone: 'bg-emerald-50 text-emerald-800 ring-emerald-200' })
  }
  const years = Number(doctor.yearsOfExperience)
  if (doctor.yearsOfExperience != null && Number.isFinite(years) && years > 0) {
    badges.push({ key: 'experience', label: `${years} năm kinh nghiệm`, icon: 'fa-solid fa-briefcase-medical', tone: 'bg-slate-50 text-slate-800 ring-slate-200' })
  }
  const rating = Number(doctor.averageRating)
  const count = Number(doctor.reviewCount) || 0
  if (count > 0 && rating >= TOP_RATING) {
    badges.push({ key: 'top', label: `Đánh giá ${rating.toFixed(1).replace('.', ',')}★`, icon: 'fa-solid fa-award', tone: 'bg-amber-50 text-amber-800 ring-amber-200' })
  }
  if (count >= MANY_REVIEWS) {
    badges.push({ key: 'many', label: 'Được đánh giá nhiều', icon: 'fa-solid fa-comments', tone: 'bg-indigo-50 text-indigo-800 ring-indigo-200' })
  }
  return badges
}
