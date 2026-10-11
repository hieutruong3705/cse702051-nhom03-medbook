import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ReceptionistLayout from '@/layouts/ReceptionistLayout.vue'
import ReceptionistOverviewView from './ReceptionistOverviewView.vue'

const mocks = vi.hoisted(() => ({
  overview: vi.fn(),
  appointmentsList: vi.fn()
}))

vi.mock('@/api/reception', () => ({
  receptionApi: {
    overview: mocks.overview,
    appointments: { list: mocks.appointmentsList }
  }
}))

const overview = {
  date: '2026-10-09',
  bookedCount: 4,
  inProgressCount: 1,
  completedCount: 7,
  cancelledCount: 2,
  unpaidInvoiceCount: 3,
  unpaidInvoiceAmount: 450000,
  collectedInvoiceCount: 5,
  collectedAmount: 780000
}

const appointmentsPage = (content) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })

function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.overview.mockResolvedValue(overview)
  mocks.appointmentsList.mockResolvedValue(appointmentsPage([
    {
      id: 10,
      patientName: 'Nguyễn Minh An',
      patientCode: 'BN000010',
      doctorName: 'Bác sĩ Lan',
      date: '2026-10-09',
      startTime: '08:00:00',
      endTime: '08:30:00'
    }
  ]))
}

const global = {
  stubs: {
    RouterLink: {
      props: ['to'],
      template: '<a :href="to"><slot /></a>'
    },
    UserMenu: { template: '<div data-test="user-menu" />' },
    NotificationBell: { template: '<div data-test="notification-bell" />' }
  }
}

describe('ReceptionistLayout', () => {
  it('có đủ sáu mục menu và không có chuông thông báo', () => {
    const wrapper = mount(ReceptionistLayout, { global, slots: { default: '<main>Nội dung</main>' } })
    expect(wrapper.text()).toContain('Tổng quan')
    expect(wrapper.text()).toContain('Lịch hẹn')
    expect(wrapper.text()).toContain('Đặt lịch hộ')
    expect(wrapper.text()).toContain('Bệnh nhân')
    expect(wrapper.text()).toContain('Hóa đơn')
    expect(wrapper.text()).toContain('Hồ sơ')
    expect(wrapper.find('[data-test="notification-bell"]').exists()).toBe(false)
  })
})

describe('ReceptionistOverviewView', () => {
  beforeEach(() => {
    resetMocks()
  })

  it('hiện trạng thái đang tải trước khi API trả dữ liệu', () => {
    mocks.overview.mockReturnValue(new Promise(() => {}))
    mocks.appointmentsList.mockReturnValue(new Promise(() => {}))
    const wrapper = mount(ReceptionistOverviewView, { global })
    expect(wrapper.text()).toContain('Đang tải tổng quan lễ tân')
  })

  it('hiện số liệu tổng quan, tiền VND và danh sách lịch BOOKED sắp tới', async () => {
    const wrapper = mount(ReceptionistOverviewView, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('Đã đặt')
    expect(wrapper.text()).toContain('4')
    expect(wrapper.text()).toContain('Đang khám')
    expect(wrapper.text()).toContain('Hoàn thành')
    expect(wrapper.text()).toContain('Đã hủy')
    expect(wrapper.text()).toContain('Hóa đơn chưa thu')
    expect(wrapper.text()).toContain('450.000')
    expect(wrapper.text()).toContain('Đã thu hôm nay')
    expect(wrapper.text()).toContain('780.000')
    expect(wrapper.text()).toContain('08:00 - 08:30')
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(wrapper.text()).toContain('Xem tất cả')
    expect(wrapper.text()).toContain('Đặt lịch hộ')
    expect(mocks.overview).toHaveBeenCalledWith(expect.objectContaining({ date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) }))
    expect(mocks.appointmentsList).toHaveBeenCalledWith(expect.objectContaining({ status: 'BOOKED', page: 0, size: 10 }))
  })

  it('hiện trạng thái rỗng khi hôm nay chưa có lịch hẹn', async () => {
    mocks.appointmentsList.mockResolvedValueOnce(appointmentsPage([]))
    const wrapper = mount(ReceptionistOverviewView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('Hôm nay chưa có lịch hẹn')
  })

  it('hiện lỗi kèm nút thử lại và tải lại được', async () => {
    mocks.overview.mockRejectedValueOnce(Object.assign(new Error('Không kết nối được máy chủ'), { status: 0 }))
    const wrapper = mount(ReceptionistOverviewView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('Không kết nối được máy chủ')

    const retry = wrapper.findAll('button').find((button) => button.text() === 'Thử lại')
    expect(retry).toBeTruthy()
    await retry.trigger('click')
    await flushPromises()
    expect(mocks.overview).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Hóa đơn chưa thu')
  })
})
