import { describe, expect, it } from 'vitest'
import { appointmentStatus, catalogStatus, encounterStatus, invoiceStatus } from './statusLabels'

describe('status labels', () => {
  it('contains labels for domain statuses used across screens', () => {
    expect(appointmentStatus.BOOKED).toBe('Đã đặt')
    expect(appointmentStatus.IN_PROGRESS).toBe('Đang khám')
    expect(invoiceStatus.UNPAID).toBe('Chưa thu')
    expect(encounterStatus.OPEN).toBe('Đang mở')
    expect(catalogStatus.INACTIVE).toBe('Ngừng dùng')
  })
})
