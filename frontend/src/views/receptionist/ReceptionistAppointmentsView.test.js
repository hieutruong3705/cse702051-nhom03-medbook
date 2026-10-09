import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ReceptionistAppointmentsView from './ReceptionistAppointmentsView.vue'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  cancel: vi.fn(),
  reschedule: vi.fn(),
  doctorsList: vi.fn(),
  slotsAvailable: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}))

vi.mock('@/api/reception', () => ({
  receptionApi: {
    appointments: {
      list: mocks.list,
      cancel: mocks.cancel,
      reschedule: mocks.reschedule
    }
  }
}))

vi.mock('@/api/doctors', () => ({ doctorsApi: { list: mocks.doctorsList } }))
vi.mock('@/api/slots', () => ({ slotsApi: { availableByDoctor: mocks.slotsAvailable } }))
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError }) }))

const booked = { id: 1, status: 'BOOKED', patientId: 1, patientName: 'Nguyễn Minh An', patientCode: 'BN000001', patientPhone: '0900111222', doctorId: 3, doctorName: 'Bác sĩ Lan', specialtyName: 'Nội tổng quát', serviceName: 'Khám tổng quát', slotId: 5, date: '2026-10-10', startTime: '08:00:00', endTime: '08:30:00' }
const completed = { ...booked, id: 2, status: 'COMPLETED', patientName: 'Trần Gia Bình', patientCode: 'BN000002' }
const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

const global = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
    UserMenu: { template: '<div />' },
    NotificationBell: { template: '<div />' }
  }
}

function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  document.body.innerHTML = ''
  mocks.list.mockResolvedValue(page([booked, completed]))
  mocks.cancel.mockResolvedValue({ ...booked, status: 'CANCELLED' })
  mocks.reschedule.mockResolvedValue({ ...booked, doctorId: 4, doctorName: 'Bác sĩ Mai', slotId: 8, date: '2026-10-11', startTime: '09:00:00', endTime: '09:30:00' })
  mocks.doctorsList.mockResolvedValue(page([
    { id: 3, fullName: 'Bác sĩ Lan', specialtyName: 'Nội tổng quát' },
    { id: 4, fullName: 'Bác sĩ Mai', specialtyName: 'Tim mạch' }
  ]))
  mocks.slotsAvailable.mockResolvedValue([{ id: 8, slotDate: '2026-10-11', startTime: '09:00:00', endTime: '09:30:00' }])
}

async function mountPage() {
  const wrapper = mount(ReceptionistAppointmentsView, { global, attachTo: document.body })
  await flushPromises()
  return wrapper
}

async function clickBody(text) {
  const dialogs = document.querySelectorAll('[role="dialog"]')
  const scope = dialogs[dialogs.length - 1] || document
  const button = Array.from(scope.querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(button, text).toBeTruthy()
  button.click()
  await flushPromises()
}

function typeBody(selector, value, event = 'input') {
  const element = document.querySelector(selector)
  expect(element, selector).toBeTruthy()
  element.value = value
  element.dispatchEvent(new Event(event))
}

describe('ReceptionistAppointmentsView', () => {
  beforeEach(resetMocks)

  it('mở trang gọi appointments.list với from/to hôm nay và hiện dữ liệu', async () => {
    const wrapper = await mountPage()
    expect(mocks.list).toHaveBeenCalledWith(expect.objectContaining({ from: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), to: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), page: 0, size: 20 }))
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).toContain('08:00 - 08:30')
    wrapper.unmount()
  })

  it('đổi bộ lọc gửi đúng tham số và bỏ tham số rỗng', async () => {
    const wrapper = await mountPage()
    await wrapper.find('#appointment-status').setValue('BOOKED')
    await wrapper.find('#appointment-keyword').setValue('An')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'BOOKED', keyword: 'An', page: 0 }))
    expect(mocks.list.mock.lastCall[0]).not.toHaveProperty('doctorId')
    wrapper.unmount()
  })

  it('có trạng thái rỗng và lỗi kèm thử lại', async () => {
    mocks.list.mockResolvedValueOnce(page([]))
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('Chưa có lịch hẹn phù hợp')

    mocks.list.mockRejectedValueOnce(Object.assign(new Error('Mất kết nối'), { status: 0 }))
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Mất kết nối')
    mocks.list.mockResolvedValueOnce(page([booked]))
    await wrapper.findAll('button').find((button) => button.text() === 'Thử lại').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    wrapper.unmount()
  })

  it('nút Hủy và Đổi lịch chỉ có ở dòng BOOKED', async () => {
    const wrapper = await mountPage()
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Hủy')).toHaveLength(1)
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Đổi lịch')).toHaveLength(1)
    wrapper.unmount()
  })

  it('hủy lịch bắt buộc lý do và thành công thì cập nhật dòng', async () => {
    const wrapper = await mountPage()
    await wrapper.find('button[aria-label="Hủy lịch #1"]').trigger('click')
    await flushPromises()
    await clickBody('Hủy lịch hẹn')
    expect(mocks.cancel).not.toHaveBeenCalled()
    expect(document.querySelector('#cancel-reason-error').textContent).toContain('Phải nhập lý do')

    typeBody('#cancel-reason', 'Bệnh nhân yêu cầu hủy')
    await clickBody('Hủy lịch hẹn')
    expect(mocks.cancel).toHaveBeenCalledWith(1, { reason: 'Bệnh nhân yêu cầu hủy' })
    expect(wrapper.text()).toContain('Đã hủy')
    wrapper.unmount()
  })

  it('hủy bị 422 thì hiện thông điệp và tải lại bảng', async () => {
    mocks.cancel.mockRejectedValueOnce(Object.assign(new Error('Lịch không còn ở trạng thái đã đặt'), { status: 422 }))
    const wrapper = await mountPage()
    await wrapper.find('button[aria-label="Hủy lịch #1"]').trigger('click')
    await flushPromises()
    typeBody('#cancel-reason', 'Bệnh nhân yêu cầu hủy')
    await clickBody('Hủy lịch hẹn')
    expect(mocks.toastError).toHaveBeenCalledWith('Lịch không còn ở trạng thái đã đặt')
    expect(mocks.list).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('đổi lịch yêu cầu khung giờ và lý do, thành công thì cập nhật ngày giờ mới', async () => {
    const wrapper = await mountPage()
    await wrapper.find('button[aria-label="Đổi lịch #1"]').trigger('click')
    await flushPromises()
    await clickBody('Đổi lịch')
    expect(mocks.reschedule).not.toHaveBeenCalled()
    expect(document.querySelector('#reschedule-reason-error').textContent).toContain('Phải nhập lý do')

    typeBody('#reschedule-date', '2026-10-11', 'input')
    await flushPromises()
    await Array.from(document.querySelectorAll('button')).find((button) => button.textContent.includes('09:00')).click()
    typeBody('#reschedule-reason', 'Đổi giờ theo yêu cầu')
    await clickBody('Đổi lịch')
    expect(mocks.reschedule).toHaveBeenCalledWith(1, { newSlotId: 8, reason: 'Đổi giờ theo yêu cầu' })
    expect(wrapper.text()).toContain('09:00 - 09:30')
    wrapper.unmount()
  })

  it('đổi lịch bị 409 thì bỏ khung giờ và tải lại SlotPicker', async () => {
    mocks.reschedule.mockRejectedValueOnce(Object.assign(new Error('Khung giờ mới vừa được đặt'), { status: 409 }))
    const wrapper = await mountPage()
    await wrapper.find('button[aria-label="Đổi lịch #1"]').trigger('click')
    await flushPromises()
    typeBody('#reschedule-date', '2026-10-11', 'input')
    await flushPromises()
    await Array.from(document.querySelectorAll('button')).find((button) => button.textContent.includes('09:00')).click()
    typeBody('#reschedule-reason', 'Đổi giờ theo yêu cầu')
    await clickBody('Đổi lịch')
    expect(document.body.textContent).toContain('Khung giờ mới vừa được đặt')
    expect(mocks.slotsAvailable.mock.calls.length).toBeGreaterThan(1)
    wrapper.unmount()
  })
})
