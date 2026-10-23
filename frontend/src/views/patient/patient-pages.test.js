import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PatientAppointmentsView from './PatientAppointmentsView.vue'
import PatientInvoicesView from './PatientInvoicesView.vue'
import PatientNotificationsView from './PatientNotificationsView.vue'
import PatientOverviewView from './PatientOverviewView.vue'
import PatientProfileView from './PatientProfileView.vue'
import PatientRecordsView from './PatientRecordsView.vue'

const mocks = vi.hoisted(() => ({
  appointmentsMine: vi.fn(),
  recordsMine: vi.fn(),
  encountersMine: vi.fn(),
  invoicesMine: vi.fn(),
  patientsMe: vi.fn(),
  patientsUpdateMe: vi.fn(),
  notificationsMine: vi.fn()
}))

vi.mock('@/api/appointments', () => ({
  appointmentsApi: { mine: mocks.appointmentsMine }
}))

vi.mock('@/api/records', () => ({
  recordsApi: { mine: mocks.recordsMine }
}))

vi.mock('@/api/encounters', () => ({
  encountersApi: { mine: mocks.encountersMine }
}))

vi.mock('@/api/invoices', () => ({
  invoicesApi: { mine: mocks.invoicesMine }
}))

vi.mock('@/api/patients', () => ({
  patientsApi: {
    me: mocks.patientsMe,
    updateMe: mocks.patientsUpdateMe
  }
}))

vi.mock('@/api/notifications', () => ({
  notificationsApi: { mine: mocks.notificationsMine }
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ displayName: 'Nguyễn Minh An' })
}))

function resetMocks() {
  mocks.appointmentsMine.mockReset()
  mocks.recordsMine.mockReset()
  mocks.encountersMine.mockReset()
  mocks.invoicesMine.mockReset()
  mocks.patientsMe.mockReset()
  mocks.patientsUpdateMe.mockReset()
  mocks.notificationsMine.mockReset()
}

describe('patient pages', () => {
  it('renders overview with upcoming appointments', async () => {
    resetMocks()
    mocks.appointmentsMine.mockResolvedValue({
      content: [{ id: 1, doctorName: 'Bác sĩ Lan', specialtyName: 'Nội tổng quát', date: '2026-10-02', startTime: '08:00', endTime: '08:30', status: 'BOOKED' }]
    })

    const wrapper = mount(PatientOverviewView)
    await flushPromises()

    expect(wrapper.text()).toContain('Xin chào, Nguyễn Minh An')
    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(mocks.appointmentsMine).toHaveBeenCalledWith(expect.objectContaining({ status: 'BOOKED', size: 5 }))
  })

  it('renders appointment list and submits filters', async () => {
    resetMocks()
    mocks.appointmentsMine.mockResolvedValue({
      content: [{ id: 2, doctorName: 'Bác sĩ Huy', serviceName: 'Khám tổng quát', status: 'COMPLETED', slot: { slotDate: '2026-10-03', startTime: '09:00', endTime: '09:30' } }],
      page: 0,
      totalPages: 1
    })

    const wrapper = mount(PatientAppointmentsView)
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Lịch hẹn của tôi')
    expect(wrapper.text()).toContain('Bác sĩ Huy')
    expect(mocks.appointmentsMine).toHaveBeenCalledTimes(2)
  })

  it('renders medical record and encounter history', async () => {
    resetMocks()
    mocks.recordsMine.mockResolvedValue({ recordCode: 'MR-001', bloodType: 'O+', allergyNotes: 'Không' })
    mocks.encountersMine.mockResolvedValue({
      content: [{ id: 1, doctorName: 'Bác sĩ Lan', chiefComplaint: 'Sốt', diagnosis: 'Cảm cúm', status: 'COMPLETED', createdAt: '2026-10-01T08:00:00Z' }]
    })

    const wrapper = mount(PatientRecordsView)
    await flushPromises()

    expect(wrapper.text()).toContain('MR-001')
    expect(wrapper.text()).toContain('Cảm cúm')
  })

  it('renders invoice list and submits filters', async () => {
    resetMocks()
    mocks.invoicesMine.mockResolvedValue({
      content: [{ id: 1, invoiceCode: 'INV-001', issuedAt: '2026-10-01', totalAmount: 150000, status: 'PAID' }],
      page: 0,
      totalPages: 1
    })

    const wrapper = mount(PatientInvoicesView)
    await flushPromises()
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('INV-001')
    expect(mocks.invoicesMine).toHaveBeenCalledTimes(2)
  })

  it('loads and submits patient profile form', async () => {
    resetMocks()
    mocks.patientsMe.mockResolvedValue({ fullName: 'Nguyễn Minh An', phone: '0900000000', email: 'an@example.com', bloodType: 'O+' })
    mocks.patientsUpdateMe.mockResolvedValue({})

    const wrapper = mount(PatientProfileView)
    await flushPromises()
    await wrapper.find('#patient-phone').setValue('0911111111')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.patientsUpdateMe).toHaveBeenCalledWith(expect.objectContaining({ phone: '0911111111' }))
    expect(wrapper.text()).toContain('Đã lưu hồ sơ.')
  })

  it('renders notifications and empty state', async () => {
    resetMocks()
    mocks.notificationsMine.mockResolvedValue({ content: [] })

    const wrapper = mount(PatientNotificationsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Chưa có thông báo')
    expect(mocks.notificationsMine).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('shows notification loading error without crashing', async () => {
    resetMocks()
    mocks.notificationsMine.mockRejectedValue(new Error('Chưa có backend thông báo'))

    const wrapper = mount(PatientNotificationsView)
    await flushPromises()

    expect(wrapper.text()).toContain('Chưa có backend thông báo')
  })
})
