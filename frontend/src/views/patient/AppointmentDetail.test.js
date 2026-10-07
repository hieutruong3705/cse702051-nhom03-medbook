import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError } from '@/api/http'
import { appointmentsApi } from '@/api/appointments'
import { doctorsApi } from '@/api/doctors'
import { slotsApi } from '@/api/slots'
import AppointmentDetail from './AppointmentDetail.vue'
import { todayIso } from './booking/bookingUtils'

const hoisted = vi.hoisted(() => ({ route: { query: {}, params: { id: '55' } }, push: vi.fn(), replace: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => hoisted.route,
    useRouter: () => ({ push: hoisted.push, replace: hoisted.replace, resolve: () => ({ matched: [] }) })
  }
})

vi.mock('@/api/appointments', () => ({ appointmentsApi: { get: vi.fn(), cancel: vi.fn(), reschedule: vi.fn() } }))
vi.mock('@/api/doctors', () => ({ doctorsApi: { list: vi.fn() } }))
vi.mock('@/api/slots', () => ({ slotsApi: { availableByDoctor: vi.fn() } }))

const addDays = (days) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return todayIso(date)
}

const booked = (overrides = {}) => ({
  id: 55,
  status: 'BOOKED',
  notes: 'Đau đầu',
  doctorId: 1,
  doctorName: 'BS. Nguyễn Văn A',
  specialtyName: 'Nội tổng quát',
  slotId: 101,
  date: addDays(5),
  startTime: '09:00:00',
  endTime: '09:30:00',
  serviceName: 'Khám tổng quát',
  createdAt: '2026-10-01T08:00:00',
  ...overrides
})

const SLOTS = [{ id: 202, slotDate: addDays(6), startTime: '10:00:00', endTime: '10:30:00' }]

const body = () => document.body
const byText = (root, text) => [...root.querySelectorAll('button')].find((button) => button.textContent.trim() === text)

async function mountView() {
  const wrapper = mount(AppointmentDetail, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  hoisted.route.params = { id: '55' }
  appointmentsApi.get.mockResolvedValue(booked())
  doctorsApi.list.mockResolvedValue({ content: [{ id: 1, fullName: 'BS. Nguyễn Văn A' }, { id: 2, fullName: 'BS. Trần Thị B' }] })
  slotsApi.availableByDoctor.mockResolvedValue(SLOTS)
})

describe('AppointmentDetail', () => {
  it('hiển thị thông tin lịch hẹn và nút Hủy/Đổi khi còn BOOKED', async () => {
    const wrapper = await mountView()
    expect(appointmentsApi.get).toHaveBeenCalledWith('55')
    expect(wrapper.text()).toContain('BS. Nguyễn Văn A')
    expect(wrapper.text()).toContain('Khám tổng quát')
    expect(wrapper.text()).toContain('Đau đầu')
    expect(byText(wrapper.element, 'Hủy lịch')).toBeTruthy()
    expect(byText(wrapper.element, 'Đổi lịch')).toBeTruthy()
    wrapper.unmount()
  })

  it.each([['COMPLETED'], ['IN_PROGRESS'], ['CANCELLED']])('không có nút Hủy/Đổi khi trạng thái %s', async (status) => {
    appointmentsApi.get.mockResolvedValue(booked({ status, cancelReason: 'Bận việc', cancelledAt: '2026-10-01T09:00:00' }))
    const wrapper = await mountView()
    expect(byText(wrapper.element, 'Hủy lịch')).toBeUndefined()
    expect(byText(wrapper.element, 'Đổi lịch')).toBeUndefined()
    wrapper.unmount()
  })

  it('lịch BOOKED đã qua giờ khám thì không cho hủy/đổi', async () => {
    appointmentsApi.get.mockResolvedValue(booked({ date: addDays(-1) }))
    const wrapper = await mountView()
    expect(byText(wrapper.element, 'Hủy lịch')).toBeUndefined()
    expect(wrapper.text()).toContain('đã qua giờ khám')
    wrapper.unmount()
  })

  it('lịch đã hủy hiển thị lý do hủy', async () => {
    appointmentsApi.get.mockResolvedValue(booked({ status: 'CANCELLED', cancelReason: 'Bận việc đột xuất', cancelledAt: '2026-10-01T09:00:00' }))
    const wrapper = await mountView()
    expect(wrapper.find('[data-test="cancel-info"]').text()).toContain('Bận việc đột xuất')
    wrapper.unmount()
  })

  it('403 và 404 hiện thông báo riêng, không có nút thử lại', async () => {
    appointmentsApi.get.mockRejectedValueOnce(new ApiRequestError({ status: 403, code: 'FORBIDDEN' }))
    const forbidden = await mountView()
    expect(forbidden.find('[data-test="load-error"]').text()).toContain('không có quyền')
    expect(byText(forbidden.element, 'Thử lại')).toBeUndefined()
    forbidden.unmount()

    appointmentsApi.get.mockRejectedValueOnce(new ApiRequestError({ status: 404, code: 'RESOURCE_NOT_FOUND' }))
    const missing = await mountView()
    expect(missing.find('[data-test="load-error"]').text()).toContain('Không tìm thấy lịch hẹn')
    missing.unmount()
  })

  it('lỗi mạng cho phép thử lại', async () => {
    appointmentsApi.get.mockRejectedValueOnce(new ApiRequestError({ status: 500, code: 'INTERNAL_ERROR' }))
    const wrapper = await mountView()
    expect(wrapper.find('[data-test="load-error"]').exists()).toBe(true)
    byText(wrapper.element, 'Thử lại').click()
    await flushPromises()
    expect(wrapper.text()).toContain('BS. Nguyễn Văn A')
    wrapper.unmount()
  })

  it('hủy lịch: mở hộp thoại, gửi lý do, cập nhật trạng thái đã hủy', async () => {
    appointmentsApi.cancel.mockResolvedValue(booked({ status: 'CANCELLED', cancelReason: 'Bận việc', cancelledAt: '2026-10-01T09:00:00' }))
    const wrapper = await mountView()

    byText(wrapper.element, 'Hủy lịch').click()
    await flushPromises()
    const reason = body().querySelector('#cancel-reason')
    reason.value = '  Bận việc  '
    reason.dispatchEvent(new Event('input'))
    byText(body(), 'Xác nhận hủy').click()
    await flushPromises()

    expect(appointmentsApi.cancel).toHaveBeenCalledWith('55', { reason: 'Bận việc' })
    expect(wrapper.find('[data-test="notice"]').text()).toContain('Đã hủy lịch hẹn')
    expect(byText(wrapper.element, 'Hủy lịch')).toBeUndefined()
    wrapper.unmount()
  })

  it('hủy lịch bị 409 (quá hạn): hiện lý do của server trong hộp thoại và tải lại trạng thái thật', async () => {
    appointmentsApi.cancel.mockRejectedValue(
      new ApiRequestError({ status: 409, code: 'CONFLICT', message: 'Không thể hủy lịch trong vòng 2 giờ trước giờ khám. Vui lòng liên hệ phòng khám!' })
    )
    const wrapper = await mountView()
    appointmentsApi.get.mockClear()

    byText(wrapper.element, 'Hủy lịch').click()
    await flushPromises()
    byText(body(), 'Xác nhận hủy').click()
    await flushPromises()

    expect(body().querySelector('[data-test="cancel-error"]').textContent).toContain('trong vòng 2 giờ')
    expect(appointmentsApi.get).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('hủy lịch bị 422 (lịch đã chuyển sang đang khám): hiện lý do và tải lại trạng thái thật', async () => {
    appointmentsApi.cancel.mockRejectedValue(
      new ApiRequestError({ status: 422, code: 'UNPROCESSABLE_ENTITY', message: 'Chỉ có thể hủy lịch hẹn ở trạng thái BOOKED (hiện tại: IN_PROGRESS)!' })
    )
    const wrapper = await mountView()
    appointmentsApi.get.mockClear()

    byText(wrapper.element, 'Hủy lịch').click()
    await flushPromises()
    byText(body(), 'Xác nhận hủy').click()
    await flushPromises()

    expect(body().querySelector('[data-test="cancel-error"]').textContent).toContain('trạng thái BOOKED')
    expect(appointmentsApi.get).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('đổi lịch: chọn ngày và khung giờ mới rồi gửi newSlotId', async () => {
    appointmentsApi.reschedule.mockResolvedValue(booked({ slotId: 202, date: addDays(6), startTime: '10:00:00', endTime: '10:30:00' }))
    const wrapper = await mountView()

    byText(wrapper.element, 'Đổi lịch').click()
    await flushPromises()
    const confirm = () => byText(body(), 'Xác nhận đổi lịch')
    expect(confirm().disabled).toBe(true)

    const date = body().querySelector('#reschedule-date')
    date.value = addDays(6)
    date.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(slotsApi.availableByDoctor).toHaveBeenCalledWith(1, addDays(6))
    body().querySelector('ul[aria-label="Khung giờ còn trống"] button').click()
    await flushPromises()
    expect(confirm().disabled).toBe(false)
    confirm().click()
    await flushPromises()

    expect(appointmentsApi.reschedule).toHaveBeenCalledWith('55', { newSlotId: 202, reason: undefined })
    expect(wrapper.find('[data-test="notice"]').text()).toContain('Đã đổi lịch hẹn')
    wrapper.unmount()
  })

  it('đổi lịch bị 409: giữ nguyên lịch cũ, báo rõ, tải lại giờ trống và bỏ lựa chọn cũ', async () => {
    appointmentsApi.reschedule.mockRejectedValue(
      new ApiRequestError({ status: 409, code: 'CONFLICT', message: 'Khung giờ mới vừa có người khác đặt. Lịch hiện tại của bạn được giữ nguyên!' })
    )
    const wrapper = await mountView()

    byText(wrapper.element, 'Đổi lịch').click()
    await flushPromises()
    const date = body().querySelector('#reschedule-date')
    date.value = addDays(6)
    date.dispatchEvent(new Event('input'))
    await flushPromises()
    body().querySelector('ul[aria-label="Khung giờ còn trống"] button').click()
    await flushPromises()
    const loadsBefore = slotsApi.availableByDoctor.mock.calls.length

    byText(body(), 'Xác nhận đổi lịch').click()
    await flushPromises()

    expect(body().querySelector('[data-test="reschedule-error"]').textContent).toContain('được giữ nguyên')
    expect(slotsApi.availableByDoctor.mock.calls.length).toBe(loadsBefore + 1)
    expect(byText(body(), 'Xác nhận đổi lịch').disabled).toBe(true) // phải chọn lại giờ
    // lịch hiện tại vẫn hiển thị nguyên vẹn
    expect(wrapper.text()).toContain('Đã đặt')
    expect(wrapper.text()).not.toContain('Đã đổi lịch hẹn')
    wrapper.unmount()
  })
})
