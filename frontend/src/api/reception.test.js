import { describe, expect, it, vi, beforeEach } from 'vitest'
import { receptionApi } from './reception'
import http from './http'

vi.mock('./http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn()
  }
}))

const data = { ok: true }

beforeEach(() => {
  http.get.mockReset().mockResolvedValue({ data })
  http.post.mockReset().mockResolvedValue({ data })
  http.patch.mockReset().mockResolvedValue({ data })
})

describe('receptionApi', () => {
  it('gọi đúng API tổng quan', async () => {
    await expect(receptionApi.overview({ date: '2026-10-09' })).resolves.toEqual(data)
    expect(http.get).toHaveBeenCalledWith('/reception/overview', { params: { date: '2026-10-09' } })
  })

  it('gọi đúng API bệnh nhân', async () => {
    await receptionApi.patients.list({ keyword: 'An', page: 0 })
    await receptionApi.patients.get(7)
    await receptionApi.patients.create({ fullName: 'Nguyễn Minh An' })

    expect(http.get).toHaveBeenNthCalledWith(1, '/reception/patients', { params: { keyword: 'An', page: 0 } })
    expect(http.get).toHaveBeenNthCalledWith(2, '/reception/patients/7')
    expect(http.post).toHaveBeenCalledWith('/reception/patients', { fullName: 'Nguyễn Minh An' })
  })

  it('gọi đúng API lịch hẹn', async () => {
    await receptionApi.appointments.list({ status: 'BOOKED' })
    await receptionApi.appointments.get(11)
    await receptionApi.appointments.create({ patientId: 1, slotId: 2, serviceId: 3 })
    await receptionApi.appointments.cancel(11, { reason: 'Bệnh nhân yêu cầu hủy' })
    await receptionApi.appointments.reschedule(11, { newSlotId: 4, reason: 'Đổi giờ theo yêu cầu' })

    expect(http.get).toHaveBeenNthCalledWith(1, '/reception/appointments', { params: { status: 'BOOKED' } })
    expect(http.get).toHaveBeenNthCalledWith(2, '/reception/appointments/11')
    expect(http.post).toHaveBeenCalledWith('/reception/appointments', { patientId: 1, slotId: 2, serviceId: 3 })
    expect(http.patch).toHaveBeenNthCalledWith(1, '/reception/appointments/11/cancel', { reason: 'Bệnh nhân yêu cầu hủy' })
    expect(http.patch).toHaveBeenNthCalledWith(2, '/reception/appointments/11/reschedule', { newSlotId: 4, reason: 'Đổi giờ theo yêu cầu' })
  })

  it('gọi đúng API hóa đơn', async () => {
    await receptionApi.invoices.list({ status: 'UNPAID' })
    await receptionApi.invoices.items(5)
    await receptionApi.invoices.collect(5)

    expect(http.get).toHaveBeenNthCalledWith(1, '/reception/invoices', { params: { status: 'UNPAID' } })
    expect(http.get).toHaveBeenNthCalledWith(2, '/reception/invoices/5/items')
    expect(http.patch).toHaveBeenCalledWith('/reception/invoices/5/collect')
  })
})
