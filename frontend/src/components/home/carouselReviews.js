/**
 * Chọn đánh giá cho băng chuyền trang chủ: ưu tiên đánh giá có nhận xét (giữ thứ tự điểm cao trước như API trả về),
 * chỉ bù đánh giá không có nhận xét khi chưa đủ `min` thẻ để băng chuyền trông không trống.
 */
export function pickCarouselReviews(list = [], limit = 6, min = 3) {
  const hasComment = (r) => typeof r?.comment === 'string' && r.comment.trim() !== ''
  const withText = list.filter(hasComment)
  const picked = withText.slice(0, limit)
  if (picked.length < min) picked.push(...list.filter((r) => !hasComment(r)).slice(0, min - picked.length))
  return picked
}
