import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import DoctorAppointmentsView from './DoctorAppointmentsView.vue'
import DoctorOverviewView from './DoctorOverviewView.vue'
import DoctorPatientsView from './DoctorPatientsView.vue'
import DoctorProfileView from './DoctorProfileView.vue'
import DoctorScheduleView from './DoctorScheduleView.vue'

const push = vi.fn()

const mocks = vi.hoisted(() => ({
  appointmentsMine: vi.fn(),
  encountersCreate: vi.fn(),
  schedulesList: vi.fn(),
  schedulesCreate: vi.fn(),
  patientsList: vi.fn(),
  usersMe: vi.fn(),
  doctorsGet: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push })
  }
})

vi.mock('@/api/appointments', () => ({
  appointmentsApi: { mine: mocks.appointmentsMine }
}))

vi.mock('@/api/encounters', () => ({
  encountersApi: { create: mocks.encountersCreate }
}))

vi.mock('@/api/doctorSchedules', () => ({
  doctorSchedulesApi: {
    list: mocks.schedulesList,
    create: mocks.schedulesCreate
  }
}))

vi.mock('@/api/patients', () => ({
  patientsApi: { list: mocks.patientsList }
}))

vi.mock('@/api/users', () => ({
  usersApi: { me: mocks.usersMe }
}))

vi.mock('@/api/doctors', () => ({
  doctorsApi: { get: mocks.doctorsGet }
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ displayName: 'Bác sĩ Lan', user: { doctorId: 7 } })
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError })
}))

function resetMocks() {
  push.mockReset()
  mocks.appointmentsMine.mockReset()
  mocks.encountersCreate.mockReset()
  mocks.schedulesList.mockReset()
  mocks.schedulesCreate.mockReset()
  mocks.patientsList.mockReset()
  mocks.usersMe.mockReset()
  mocks.doctorsGet.mockReset()
  mocks.toastSuccess.mockReset()
  mocks.toastError.mockReset()
}

describe('doctor pages', () => {
  it('renders today overview without cancelled appointments', async () => {
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

  it('creates a doctor schedule with previewed slots', async () => {
    resetMocks()
    mocks.schedulesList.mockResolvedValue({ content: [] })
    mocks.schedulesCreate.mockResolvedValue({})

    const wrapper = mount(DoctorScheduleView)
    await flushPromises()
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

  it('renders appointment list and starts an encounter', async () => {
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

  it('filters responsible patients', async () => {
    resetMocks()
    mocks.patientsList.mockResolvedValue({
      content: [{ id: 1, fullName: 'Nguyễn Minh An', patientCode: 'PT-001', phone: '0900000000' }],
      page: 0,
      totalPages: 1
    })

    const wrapper = mount(DoctorPatientsView)
    await flushPromises()
    await wrapper.find('#doctor-patient-keyword').setValue('An')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('PT-001')
    expect(mocks.patientsList).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'An' }))
  })

  it('renders doctor profile from account and doctor APIs', async () => {
    resetMocks()
    mocks.usersMe.mockResolvedValue({ fullName: 'Bác sĩ Lan', email: 'lan@example.com', phone: '0988888888' })
    mocks.doctorsGet.mockResolvedValue({ specialtyName: 'Nội tổng quát', licenseNumber: 'CCHN-001', bio: '10 năm kinh nghiệm' })

    const wrapper = mount(DoctorProfileView)
    await flushPromises()

    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(wrapper.text()).toContain('Nội tổng quát')
    expect(mocks.doctorsGet).toHaveBeenCalledWith(7)
  })
})
