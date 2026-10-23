import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DoctorAppointmentsView from './DoctorAppointmentsView.vue'
import DoctorNotificationsView from './DoctorNotificationsView.vue'
import DoctorOverviewView from './DoctorOverviewView.vue'
import DoctorPatientDetailView from './DoctorPatientDetailView.vue'
import DoctorPatientsView from './DoctorPatientsView.vue'
import DoctorProfileView from './DoctorProfileView.vue'
import DoctorScheduleView from './DoctorScheduleView.vue'

const push = vi.fn()
const route = { params: { id: '12' }, query: {} }

const mocks = vi.hoisted(() => ({
  appointmentsMine: vi.fn(),
  encountersCreate: vi.fn(),
  encountersList: vi.fn(),
  schedulesList: vi.fn(),
  schedulesCreate: vi.fn(),
  schedulesUpdate: vi.fn(),
  schedulesRemove: vi.fn(),
  schedulesSlots: vi.fn(),
  schedulesAddBreak: vi.fn(),
  schedulesRemoveBreak: vi.fn(),
  daysOff: vi.fn(),
  addDayOff: vi.fn(),
  removeDayOff: vi.fn(),
  patientsList: vi.fn(),
  patientsGet: vi.fn(),
  recordsByPatient: vi.fn(),
  usersMe: vi.fn(),
  doctorsGet: vi.fn(),
  notificationsMine: vi.fn(),
  notificationsUnread: vi.fn(),
  notificationsMarkRead: vi.fn(),
  notificationsMarkAll: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push }), useRoute: () => route }
})

vi.mock('@/api/appointments', () => ({ appointmentsApi: { mine: mocks.appointmentsMine } }))
vi.mock('@/api/encounters', () => ({ encountersApi: { create: mocks.encountersCreate, list: mocks.encountersList } }))
vi.mock('@/api/doctorSchedules', () => ({
  doctorSchedulesApi: {
    list: mocks.schedulesList,
    create: mocks.schedulesCreate,
    update: mocks.schedulesUpdate,
    remove: mocks.schedulesRemove,
    slots: mocks.schedulesSlots,
    addBreak: mocks.schedulesAddBreak,
    removeBreak: mocks.schedulesRemoveBreak,
    daysOff: mocks.daysOff,
    addDayOff: mocks.addDayOff,
    removeDayOff: mocks.removeDayOff
  }
}))
vi.mock('@/api/patients', () => ({ patientsApi: { list: mocks.patientsList, get: mocks.patientsGet } }))
vi.mock('@/api/records', () => ({ recordsApi: { byPatient: mocks.recordsByPatient } }))
vi.mock('@/api/users', () => ({ usersApi: { me: mocks.usersMe } }))
vi.mock('@/api/doctors', () => ({ doctorsApi: { get: mocks.doctorsGet } }))
vi.mock('@/api/notifications', () => ({
  notificationsApi: {
    mine: mocks.notificationsMine,
    unreadCount: mocks.notificationsUnread,
    markRead: mocks.notificationsMarkRead,
    markAllRead: mocks.notificationsMarkAll
  }
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ displayName: 'Bác sĩ Lan', user: { doctorId: 7 }, isAuthenticated: true, primaryRole: 'DOCTOR', roles: ['DOCTOR'] })
}))
vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError })
}))

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
const apiError = (status, message, details = {}) => Object.assign(new Error(message), { status, details })

// Đúng hình dạng DoctorScheduleDTO: slotMinutes, breaks[{id,startTime,endTime,reason}], totalSlots, bookedSlots.
const schedule = {
  id: 31, doctorId: 7, workDate: '2026-11-02', startTime: '08:00:00', endTime: '11:00:00', slotMinutes: 30,
  breaks: [{ id: 5, startTime: '09:00:00', endTime: '09:30:00', reason: 'Họp khoa' }], totalSlots: 5, bookedSlots: 1
}

function resetMocks() {
  push.mockReset()
  Object.values(mocks).forEach((mock) => mock.mockReset())
  document.body.innerHTML = ''
  route.params.id = '12'
  mocks.schedulesList.mockResolvedValue(page([schedule]))
  mocks.daysOff.mockResolvedValue([{ id: 4, date: '2026-11-20', reason: 'Hội thảo' }])
  mocks.notificationsUnread.mockResolvedValue({ count: 3 })
  mocks.notificationsMine.mockResolvedValue(page([]))
}

const clickInDialog = async (text) => {
  const dialogs = document.querySelectorAll('[role="dialog"]')
  const button = Array.from(dialogs[dialogs.length - 1].querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(button, `nút "${text}" trong hộp thoại`).toBeTruthy()
  button.click()
  await flushPromises()
}
const typeInBody = (selector, value) => {
  const element = document.querySelector(selector)
  expect(element, selector).toBeTruthy()
  element.value = value
  element.dispatchEvent(new Event('input'))
}

describe('trang của bác sĩ', () => {
  it('tổng quan hôm nay không tính lịch đã hủy', async () => {
    resetMocks()
    mocks.appointmentsMine.mockResolvedValue({
      content: [
        { id: 1, patientName: 'Nguyễn Minh An', serviceName: 'Khám tổng quát', startTime: '08:00', endTime: '08:30', status: 'BOOKED' },
        { id: 2, patientName: 'Đã hủy', startTime: '09:00', endTime: '09:30', status: 'CANCELLED' }
      ]
    })

    const wrapper = mount(DoctorOverviewView)
    await flushPromises()

    expect(wrapper.text()).toContain('Xin chào, Bác sĩ Lan')
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).not.toContain('Đã hủy')
  })

  describe('lịch làm việc', () => {
    it('hiện ca, số slot đã đặt, giờ nghỉ và ngày nghỉ', async () => {
      resetMocks()
      const wrapper = mount(DoctorScheduleView)
      await flushPromises()
      expect(mocks.schedulesList).toHaveBeenCalledWith({ page: 0, size: 20 })
      expect(wrapper.text()).toContain('08:00 – 11:00')
      expect(wrapper.text()).toContain('1/5 đã đặt')
      expect(wrapper.text()).toContain('09:00–09:30')
      expect(wrapper.text()).toContain('Hội thảo')
    })

    it('tạo ca với các slot đã xem trước', async () => {
      resetMocks()
      mocks.schedulesList.mockResolvedValue(page([]))
      mocks.schedulesCreate.mockResolvedValue({})

      const wrapper = mount(DoctorScheduleView)
      await flushPromises()
      expect(wrapper.text()).toContain('Chưa có ca làm việc')
      expect(wrapper.text()).toContain('Xem trước: 6 slot')
      await wrapper.find('#schedule-date').setValue('2026-10-02')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(mocks.schedulesCreate).toHaveBeenCalledWith({
        workDate: '2026-10-02',
        startTime: '08:00',
        endTime: '11:00',
        slotMinutes: 30,
        breaks: []
      })
      expect(mocks.toastSuccess).toHaveBeenCalledWith('Đã tạo ca làm việc')
    })

    it('ca chồng giờ (422) và lỗi theo trường (400) hiện đúng chỗ, không báo thành công', async () => {
      resetMocks()
      mocks.schedulesCreate.mockRejectedValueOnce(apiError(422, 'Ca làm việc chồng giờ với ca 08:00–11:00 đã có trong ngày này!'))
      const wrapper = mount(DoctorScheduleView)
      await flushPromises()
      await wrapper.find('#schedule-date').setValue('2026-11-02')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(wrapper.text()).toContain('Ca làm việc chồng giờ với ca 08:00–11:00')
      expect(mocks.toastSuccess).not.toHaveBeenCalled()

      mocks.schedulesCreate.mockRejectedValueOnce(apiError(400, 'Dữ liệu đầu vào không hợp lệ', { slotMinutes: 'Số phút mỗi slot phải từ 5 đến 120 và chia hết cho 5' }))
      await wrapper.find('#slot-minutes').setValue('7')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(wrapper.find('#slot-minutes-error').text()).toContain('chia hết cho 5')
    })

    it('sửa giờ ca; ca đã có lịch đặt (409) thì giữ hộp thoại và hiện lý do', async () => {
      resetMocks()
      mocks.schedulesUpdate.mockRejectedValueOnce(apiError(409, 'Không thể sửa ca vì đã có lịch hẹn được đặt trong khoảng giờ này.'))
      const wrapper = mount(DoctorScheduleView, { attachTo: document.body })
      await flushPromises()
      await wrapper.findAll('button').find((button) => button.text() === 'Sửa giờ').trigger('click')
      await flushPromises()
      expect(document.querySelector('#edit-start').value).toBe('08:00')
      typeInBody('#edit-end', '12:00')
      await clickInDialog('Lưu')
      expect(mocks.schedulesUpdate).toHaveBeenCalledWith(31, { startTime: '08:00', endTime: '12:00', slotMinutes: 30 })
      expect(document.body.textContent).toContain('đã có lịch hẹn được đặt')

      mocks.schedulesUpdate.mockResolvedValueOnce({})
      await clickInDialog('Lưu')
      expect(mocks.toastSuccess).toHaveBeenCalledWith('Đã cập nhật ca làm việc')
      wrapper.unmount()
    })

    it('xóa ca; bị từ chối 409 thì báo lý do của máy chủ và tải lại danh sách', async () => {
      resetMocks()
      mocks.schedulesRemove.mockRejectedValueOnce(apiError(409, 'Không thể xóa ca vì đã có lịch hẹn được đặt trong khoảng giờ này.'))
      const wrapper = mount(DoctorScheduleView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label^="Xóa ca "]').trigger('click')
      await flushPromises()
      await clickInDialog('Xóa ca')
      expect(mocks.schedulesRemove).toHaveBeenCalledWith(31)
      expect(mocks.toastError).toHaveBeenCalledWith(expect.stringContaining('đã có lịch hẹn'), 'Không xóa được ca')
      expect(mocks.schedulesList).toHaveBeenCalledTimes(2)

      mocks.schedulesRemove.mockResolvedValueOnce({})
      await wrapper.find('button[aria-label^="Xóa ca "]').trigger('click')
      await flushPromises()
      await clickInDialog('Xóa ca')
      expect(mocks.toastSuccess).toHaveBeenCalledWith('Đã xóa ca làm việc')
      wrapper.unmount()
    })

    it('thêm và xóa giờ nghỉ', async () => {
      resetMocks()
      mocks.schedulesAddBreak.mockResolvedValue({})
      mocks.schedulesRemoveBreak.mockResolvedValue({})
      const wrapper = mount(DoctorScheduleView, { attachTo: document.body })
      await flushPromises()
      await wrapper.findAll('button').find((button) => button.text() === 'Thêm giờ nghỉ').trigger('click')
      await flushPromises()
      await clickInDialog('Thêm')
      expect(mocks.schedulesAddBreak).not.toHaveBeenCalled()
      expect(document.body.textContent).toContain('Phải nhập giờ bắt đầu và giờ kết thúc nghỉ.')

      typeInBody('#new-break-start', '10:00')
      typeInBody('#new-break-end', '10:30')
      typeInBody('#new-break-reason', 'Giao ban')
      await clickInDialog('Thêm')
      expect(mocks.schedulesAddBreak).toHaveBeenCalledWith(31, { startTime: '10:00', endTime: '10:30', reason: 'Giao ban' })

      await wrapper.find('button[aria-label^="Xóa giờ nghỉ 09:00"]').trigger('click')
      await flushPromises()
      expect(mocks.schedulesRemoveBreak).toHaveBeenCalledWith(31, 5)
      wrapper.unmount()
    })

    it('xem slot của ca: trống hoặc đã đặt kèm mã lịch hẹn, không có tên bệnh nhân', async () => {
      resetMocks()
      mocks.schedulesSlots.mockResolvedValue([
        { id: 1, startTime: '08:00:00', endTime: '08:30:00', status: 'BOOKED', appointmentId: 77 },
        { id: 2, startTime: '08:30:00', endTime: '09:00:00', status: 'AVAILABLE', appointmentId: null }
      ])
      const wrapper = mount(DoctorScheduleView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label^="Xem slot của ca"]').trigger('click')
      await flushPromises()
      expect(mocks.schedulesSlots).toHaveBeenCalledWith(31)
      expect(document.body.textContent).toContain('Đã đặt · lịch #77')
      expect(document.body.textContent).toContain('Còn trống')
      wrapper.unmount()
    })

    it('đăng ký và xóa ngày nghỉ; ngày đã có lịch hẹn (409) hiện lý do', async () => {
      resetMocks()
      mocks.addDayOff.mockRejectedValueOnce(apiError(409, 'Ngày 2026-11-02 đã có 1 lịch hẹn được đặt.'))
      const wrapper = mount(DoctorScheduleView)
      await flushPromises()
      const dayOffForm = wrapper.findAll('form')[1]
      await dayOffForm.trigger('submit')
      expect(wrapper.find('#day-off-date-error').text()).toContain('Phải chọn ngày nghỉ')

      await wrapper.find('#day-off-date').setValue('2026-11-02')
      await wrapper.find('#day-off-reason').setValue('Việc riêng')
      await dayOffForm.trigger('submit')
      await flushPromises()
      expect(mocks.addDayOff).toHaveBeenCalledWith({ date: '2026-11-02', reason: 'Việc riêng' })
      expect(wrapper.text()).toContain('đã có 1 lịch hẹn được đặt')

      mocks.addDayOff.mockResolvedValueOnce({ id: 9 })
      await dayOffForm.trigger('submit')
      await flushPromises()
      expect(mocks.toastSuccess).toHaveBeenCalledWith('Đã đăng ký ngày nghỉ')
      expect(mocks.daysOff).toHaveBeenCalledTimes(2)

      mocks.removeDayOff.mockResolvedValue({})
      await wrapper.find('button[aria-label^="Xóa ngày nghỉ"]').trigger('click')
      await flushPromises()
      expect(mocks.removeDayOff).toHaveBeenCalledWith(4)
    })

    it('lỗi tải danh sách ca hiện trong bảng', async () => {
      resetMocks()
      mocks.schedulesList.mockRejectedValueOnce(apiError(500, 'x'))
      const wrapper = mount(DoctorScheduleView)
      await flushPromises()
      expect(wrapper.text()).toContain('Hệ thống đang bận')
    })
  })

  it('danh sách lịch khám và bắt đầu khám', async () => {
    resetMocks()
    mocks.appointmentsMine.mockResolvedValue({
      content: [{ id: 5, patientName: 'Trần Gia Bình', serviceName: 'Tái khám', status: 'BOOKED', slot: { slotDate: '2026-10-03', startTime: '10:00', endTime: '10:30' } }],
      page: 0,
      totalPages: 1
    })
    mocks.encountersCreate.mockResolvedValue({ id: 99 })

    const wrapper = mount(DoctorAppointmentsView)
    await flushPromises()
    const startButton = wrapper.findAll('button').find((button) => button.text().includes('Bắt đầu khám'))
    await startButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Trần Gia Bình')
    expect(mocks.encountersCreate).toHaveBeenCalledWith({ appointmentId: 5 })
    expect(push).toHaveBeenCalledWith('/doctor/encounters/99')
  })

  describe('bệnh nhân', () => {
    it('lọc bệnh nhân phụ trách và có liên kết sang trang chi tiết', async () => {
      resetMocks()
      mocks.patientsList.mockResolvedValue(page([{ id: 12, fullName: 'Nguyễn Minh An', patientCode: 'BN000012', genderCode: 'MALE', phone: '0900000000' }]))

      const wrapper = mount(DoctorPatientsView)
      await flushPromises()
      await wrapper.find('#doctor-patient-keyword').setValue('An')
      await wrapper.find('form').trigger('submit')
      await flushPromises()

      expect(wrapper.text()).toContain('BN000012')
      expect(wrapper.text()).toContain('Nam')
      expect(wrapper.find('a[href="/doctor/patients/12"]').exists()).toBe(true)
      expect(mocks.patientsList).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'An' }))
    })

    it('chi tiết: hồ sơ, bệnh án và lịch sử lần khám có liên kết sang màn hình khám', async () => {
      resetMocks()
      mocks.patientsGet.mockResolvedValue({ id: 12, patientCode: 'BN000012', fullName: 'Nguyễn Minh An', dateOfBirth: '1990-01-01', genderCode: 'MALE', phone: '0934567890', allergies: 'Phấn hoa' })
      mocks.recordsByPatient.mockResolvedValue({ id: 40, recordCode: 'BA000012', patientId: 12, chronicConditions: 'Tăng huyết áp', medicalHistory: 'Mổ ruột thừa 2015' })
      mocks.encountersList.mockResolvedValue(page([
        { id: 70, appointmentId: 5, medicalRecordId: 40, patientId: 12, encounterAt: '2026-10-01T09:00:00', chiefComplaint: 'Đau đầu', diagnosis: 'Thiếu ngủ', status: 'COMPLETED' },
        { id: 71, appointmentId: 6, medicalRecordId: 40, patientId: 12, encounterAt: '2026-10-05T09:00:00', chiefComplaint: 'Tái khám', diagnosis: null, status: 'OPEN' }
      ]))

      const wrapper = mount(DoctorPatientDetailView)
      await flushPromises()

      expect(mocks.patientsGet).toHaveBeenCalledWith('12')
      expect(mocks.recordsByPatient).toHaveBeenCalledWith('12')
      expect(mocks.encountersList).toHaveBeenCalledWith({ medicalRecordId: 40, size: 50 })
      expect(wrapper.text()).toContain('Nguyễn Minh An')
      expect(wrapper.text()).toContain('Tăng huyết áp')
      expect(wrapper.text()).toContain('Thiếu ngủ')
      expect(wrapper.find('a[href="/doctor/encounters/70"]').text()).toBe('Xem lần khám')
      expect(wrapper.find('a[href="/doctor/encounters/71"]').text()).toBe('Tiếp tục khám')
    })

    it('bệnh nhân ngoài phạm vi (403): chỉ hiện thông báo, không lộ dữ liệu, không gọi lịch sử khám', async () => {
      resetMocks()
      mocks.patientsGet.mockResolvedValue({ id: 12, patientCode: 'BN000012', fullName: 'Nguyễn Minh An', phone: '0934567890' })
      mocks.recordsByPatient.mockRejectedValue(apiError(403, 'Bạn không phụ trách bệnh nhân này!'))

      const wrapper = mount(DoctorPatientDetailView)
      await flushPromises()

      expect(wrapper.text()).toContain('Bạn không phụ trách bệnh nhân này')
      expect(wrapper.text()).not.toContain('Nguyễn Minh An')
      expect(wrapper.text()).not.toContain('0934567890')
      expect(mocks.encountersList).not.toHaveBeenCalled()
    })

    it('lỗi mạng có nút thử lại; không tìm thấy thì báo rõ', async () => {
      resetMocks()
      mocks.patientsGet.mockRejectedValueOnce(apiError(0, 'Không kết nối được máy chủ.'))
      const wrapper = mount(DoctorPatientDetailView)
      await flushPromises()
      expect(wrapper.text()).toContain('Thử lại')

      mocks.patientsGet.mockRejectedValueOnce(apiError(404, 'x'))
      await wrapper.findAll('button').find((button) => button.text() === 'Thử lại').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('Không tìm thấy bệnh nhân.')
    })
  })

  it('hồ sơ bác sĩ lấy từ API tài khoản và API bác sĩ', async () => {
    resetMocks()
    mocks.usersMe.mockResolvedValue({ fullName: 'Bác sĩ Lan', email: 'lan@example.com', phone: '0988888888' })
    mocks.doctorsGet.mockResolvedValue({ specialtyName: 'Nội tổng quát', bio: '10 năm kinh nghiệm' })

    const wrapper = mount(DoctorProfileView)
    await flushPromises()

    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(wrapper.text()).toContain('Nội tổng quát')
    expect(mocks.doctorsGet).toHaveBeenCalledWith(7)
  })

  describe('thông báo', () => {
    // Đúng hình dạng NotificationDTO: chưa đọc khi readAt rỗng.
    const unread = { id: 1, type: 'APPOINTMENT_BOOKED', title: 'Có lịch khám mới', message: 'Bệnh nhân An đã đặt lịch khám lúc 09:00 ngày 02/11/2026.', appointmentId: 5, createdAt: '2026-10-07T08:00:00', readAt: null }
    const read = { id: 2, type: 'REMINDER_24H', title: 'Nhắc lịch khám', message: 'Bạn có lịch khám vào ngày mai.', appointmentId: 6, createdAt: '2026-10-06T08:00:00', readAt: '2026-10-06T09:00:00' }

    it('chuông ở thanh trên hiện số chưa đọc và dẫn tới trang thông báo', async () => {
      resetMocks()
      const wrapper = mount(DoctorNotificationsView)
      await flushPromises()
      const bell = wrapper.find('[data-test="notification-bell"]')
      expect(bell.attributes('href')).toBe('/doctor/notifications')
      expect(bell.attributes('aria-label')).toBe('Thông báo, 3 chưa đọc')
      expect(bell.text()).toContain('3')
      wrapper.unmount()
    })

    it('danh sách: đánh dấu một thông báo đã đọc và cập nhật lại số trên chuông', async () => {
      resetMocks()
      mocks.notificationsMine.mockResolvedValue(page([{ ...unread }, { ...read }]))
      mocks.notificationsMarkRead.mockResolvedValue({ ...unread, readAt: '2026-10-07T10:00:00' })
      const wrapper = mount(DoctorNotificationsView)
      await flushPromises()
      expect(mocks.notificationsMine).toHaveBeenCalledWith({ page: 0, size: 20 })
      expect(wrapper.text()).toContain('Có lịch khám mới')
      expect(wrapper.text()).toContain('Đã đọc')

      mocks.notificationsUnread.mockResolvedValue({ count: 2 })
      await wrapper.find('button[aria-label="Đánh dấu đã đọc: Có lịch khám mới"]').trigger('click')
      await flushPromises()
      expect(mocks.notificationsMarkRead).toHaveBeenCalledWith(1)
      expect(wrapper.find('button[aria-label="Đánh dấu đã đọc: Có lịch khám mới"]').exists()).toBe(false)
      expect(wrapper.find('[data-test="notification-bell"]').text()).toContain('2')
      wrapper.unmount()
    })

    it('đánh dấu tất cả đã đọc, lọc chỉ chưa đọc, trạng thái rỗng và lỗi có nút thử lại', async () => {
      resetMocks()
      mocks.notificationsMine.mockResolvedValue(page([{ ...unread }]))
      mocks.notificationsMarkAll.mockResolvedValue({})
      const wrapper = mount(DoctorNotificationsView)
      await flushPromises()

      await wrapper.findAll('button').find((button) => button.text() === 'Đánh dấu tất cả đã đọc').trigger('click')
      await flushPromises()
      expect(mocks.notificationsMarkAll).toHaveBeenCalled()

      mocks.notificationsMine.mockResolvedValueOnce(page([]))
      await wrapper.find('#notifications-unread-only').setValue(true)
      await flushPromises()
      expect(mocks.notificationsMine).toHaveBeenLastCalledWith({ page: 0, size: 20, unreadOnly: true })
      expect(wrapper.text()).toContain('Chưa có thông báo')

      mocks.notificationsMine.mockRejectedValueOnce(apiError(0, 'Không kết nối được máy chủ.'))
      await wrapper.find('#notifications-unread-only').setValue(false)
      await flushPromises()
      expect(wrapper.text()).toContain('Thử lại')
      wrapper.unmount()
    })
  })
})
