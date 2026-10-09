import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ReceptionistBookingView from './ReceptionistBookingView.vue'

const routeState = vi.hoisted(() => ({ query: {} }))
const mocks = vi.hoisted(() => ({
  patientsGet: vi.fn(),
  patientsList: vi.fn(),
  appointmentCreate: vi.fn(),
  servicesList: vi.fn(),
  specialtiesList: vi.fn(),
  doctorsList: vi.fn(),
  slotsAvailable: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  RouterLink: {
    props: ['to'],
    template: '<a :href="to"><slot /></a>'
  }
}))

vi.mock('@/api/reception', () => ({
  receptionApi: {
    patients: { get: mocks.patientsGet, list: mocks.patientsList },
    appointments: { create: mocks.appointmentCreate }
  }
}))

vi.mock('@/api/catalog', () => ({
  catalogApi: {
    services: { list: mocks.servicesList },
    specialties: { list: mocks.specialtiesList }
  }
}))

vi.mock('@/api/doctors', () => ({
  doctorsApi: { list: mocks.doctorsList }
}))

vi.mock('@/api/slots', () => ({
  slotsApi: { availableByDoctor: mocks.slotsAvailable }
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: vi.fn() })
}))

const patient = { id: 1, patientCode: 'BN000001', fullName: 'Nguyễn Minh An', dateOfBirth: '1990-01-01', genderCode: 'MALE', phone: '0900111222', hasAccount: false }
const service = { id: 2, name: 'Khám tổng quát', durationMinutes: 30, price: 150000 }
const doctor = { id: 3, fullName: 'Bác sĩ Lan', specialtyId: 4, specialtyName: 'Nội tổng quát' }
const slot = { id: 5, doctorId: 3, slotDate: '2026-10-10', startTime: '08:00:00', endTime: '08:30:00', status: 'AVAILABLE' }
const page = (content) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: 1 })

const global = {
  stubs: {
    UserMenu: { template: '<div />' },
    NotificationBell: { template: '<div />' }
  }
}

function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  routeState.query = {}
  document.body.innerHTML = ''
  mocks.patientsGet.mockResolvedValue(patient)
  mocks.patientsList.mockResolvedValue(page([patient]))
  mocks.appointmentCreate.mockResolvedValue({ id: 99, ...patient, serviceName: service.name })
  mocks.servicesList.mockResolvedValue(page([service]))
  mocks.specialtiesList.mockResolvedValue(page([{ id: 4, name: 'Nội tổng quát' }]))
  mocks.doctorsList.mockResolvedValue(page([doctor]))
  mocks.slotsAvailable.mockResolvedValue([slot])
}

async function mountBooking(query = {}) {
  routeState.query = query
  const wrapper = mount(ReceptionistBookingView, { global, attachTo: document.body })
  await flushPromises()
  return wrapper
}

async function chooseBaseFlow(wrapper) {
  await wrapper.find('#booking-service').setValue('2')
  await flushPromises()
  await wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục').trigger('click')
  await flushPromises()
  await wrapper.find('#booking-doctor').setValue('3')
  await wrapper.find('#booking-date').setValue('2026-10-10')
  await flushPromises()
  const slotButton = wrapper.findAll('button').find((button) => button.text().includes('08:00'))
  expect(slotButton).toBeTruthy()
  await slotButton.trigger('click')
  await flushPromises()
  await wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục').trigger('click')
  await flushPromises()
}

describe('ReceptionistBookingView', () => {
  beforeEach(resetMocks)

  it('patientId hợp lệ chọn sẵn bệnh nhân và vào bước dịch vụ', async () => {
    const wrapper = await mountBooking({ patientId: '1' })
    expect(mocks.patientsGet).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('Bước 2')
    expect(wrapper.text()).toContain('Khám tổng quát')
    wrapper.unmount()
  })

  it('patientId lạ hiển thị thông báo và ở bước chọn bệnh nhân', async () => {
    mocks.patientsGet.mockRejectedValueOnce(Object.assign(new Error('Không tìm thấy'), { status: 404 }))
    const wrapper = await mountBooking({ patientId: '404' })
    expect(wrapper.text()).toContain('Không tìm thấy bệnh nhân')
    expect(wrapper.text()).toContain('Tìm bệnh nhân')
    wrapper.unmount()
  })

  it('tìm và chọn bệnh nhân ở bước 1', async () => {
    const wrapper = await mountBooking()
    await wrapper.find('#booking-patient-keyword').setValue('An')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.patientsList).toHaveBeenCalledWith({ keyword: 'An', page: 0, size: 10 })
    await wrapper.findAll('button').find((button) => button.text() === 'Chọn').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('BN000001')
    wrapper.unmount()
  })

  it('gửi đúng patientId, slotId, serviceId và notes khi xác nhận', async () => {
    const wrapper = await mountBooking({ patientId: '1' })
    await chooseBaseFlow(wrapper)
    await wrapper.find('#booking-notes').setValue('Cần gọi lại trước giờ khám')
    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(mocks.appointmentCreate).toHaveBeenCalledWith({ patientId: 1, slotId: 5, serviceId: 2, notes: 'Cần gọi lại trước giờ khám' })
    expect(wrapper.text()).toContain('Đã đặt lịch')
    wrapper.unmount()
  })

  it('409 khi xác nhận quay về bước 3, bỏ khung giờ và tải lại danh sách', async () => {
    mocks.appointmentCreate.mockRejectedValueOnce(Object.assign(new Error('Khung giờ vừa được đặt'), { status: 409 }))
    const wrapper = await mountBooking({ patientId: '1' })
    await chooseBaseFlow(wrapper)
    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Bác sĩ và giờ')
    expect(wrapper.text()).toContain('Khung giờ vừa được đặt')
    expect(mocks.slotsAvailable.mock.calls.length).toBeGreaterThan(1)
    wrapper.unmount()
  })

  it('nhấn xác nhận hai lần nhanh chỉ gọi API một lần', async () => {
    let resolveCreate
    mocks.appointmentCreate.mockReturnValueOnce(new Promise((resolve) => { resolveCreate = resolve }))
    const wrapper = await mountBooking({ patientId: '1' })
    await chooseBaseFlow(wrapper)
    const confirm = wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch')
    await confirm.trigger('click')
    await confirm.trigger('click')
    expect(mocks.appointmentCreate).toHaveBeenCalledTimes(1)
    resolveCreate({ id: 100 })
    await flushPromises()
    wrapper.unmount()
  })
})
