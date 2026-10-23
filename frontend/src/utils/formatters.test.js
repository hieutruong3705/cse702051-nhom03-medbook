import { describe, expect, it } from 'vitest'
import { formatCurrency, formatDate, formatDateTime, formatTime } from './formatters'

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
