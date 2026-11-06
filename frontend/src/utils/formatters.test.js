import { describe, expect, it } from 'vitest'
import { formatCurrency, formatDate, formatDateTime, formatTime, localIsoDate } from './formatters'

describe('formatters', () => {
  it('formats dates and times for vi-VN', () => {
    expect(formatDate('2026-10-01T08:30:00Z')).toBeTruthy()
    expect(formatDateTime('2026-10-01T08:30:00Z')).toBeTruthy()
    expect(formatTime('09:15:00')).toBe('09:15')
  })

  it('formats VND currency without decimals', () => {
    expect(formatCurrency(150000)).toContain('150.000')
  })

  it('returns an empty string for missing temporal values', () => {
    expect(formatDate()).toBe('')
    expect(formatDateTime()).toBe('')
    expect(formatTime()).toBe('')
  })
})

describe('localIsoDate', () => {
  it('lấy ngày theo giờ máy, không đổi sang UTC', () => {
    // 00:30 ngày 5/11 giờ máy: toISOString() ở múi giờ dương sẽ cho 4/11
    expect(localIsoDate(new Date(2026, 10, 5, 0, 30))).toBe('2026-11-05')
    expect(localIsoDate(new Date(2026, 0, 9, 23, 59))).toBe('2026-01-09')
  })
})
