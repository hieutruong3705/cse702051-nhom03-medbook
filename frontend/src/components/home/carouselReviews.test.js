import { describe, expect, it } from 'vitest'
import { pickCarouselReviews } from './carouselReviews'

const r = (id, comment) => ({ id, rating: 5, comment })

describe('pickCarouselReviews', () => {
  it('ưu tiên đánh giá có nhận xét và giữ thứ tự', () => {
    const list = [r(1, null), r(2, 'Tốt'), r(3, '  '), r(4, 'Rất tốt'), r(5, 'Ổn'), r(6, 'Hài lòng')]
    expect(pickCarouselReviews(list).map((x) => x.id)).toEqual([2, 4, 5, 6])
  })

  it('giới hạn số thẻ', () => {
    const list = Array.from({ length: 10 }, (_, i) => r(i, `Nhận xét ${i}`))
    expect(pickCarouselReviews(list, 6)).toHaveLength(6)
  })

  it('bù đánh giá không có nhận xét khi có ít hơn 3 đánh giá có nhận xét', () => {
    const list = [r(1, null), r(2, 'Tốt'), r(3, null), r(4, null)]
    expect(pickCarouselReviews(list).map((x) => x.id)).toEqual([2, 1, 3])
  })
})
