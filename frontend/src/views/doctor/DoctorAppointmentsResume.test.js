import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { appointmentsApi } from '@/api/appointments'
import { encountersApi } from '@/api/encounters'
import DoctorAppointmentsView from './DoctorAppointmentsView.vue'

const hoisted = vi.hoisted(() => ({ push: vi.fn(), toastError: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push: hoisted.push }) }
})
vi.mock('@/api/appointments', () => ({ appointmentsApi: { mine: vi.fn() } }))
vi.mock('@/api/encounters', () => ({ encountersApi: { create: vi.fn(), list: vi.fn() } }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ displayName: 'Bác sĩ Lan', user: { doctorId: 7 } }) }))
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ success: vi.fn(), error: hoisted.toastError }) }))

const row = (id, status) => ({
  id,
  status,
  patientName: `Bệnh nhân ${id}`,
  serviceName: 'Khám tổng quát',
  date: '2026-10-02',
  startTime: '09:00:00',
  endTime: '09:30:00'
})

const button = (wrapper, rowId) => wrapper.findAll('tbody tr').find((tr) => tr.text().includes(`Bệnh nhân ${rowId}`)).find('button')

async function mountView(rows) {
  appointmentsApi.mine.mockResolvedValue({ content: rows, totalPages: 1 })
  const wrapper = mount(DoctorAppointmentsView)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('DoctorAppointmentsView: mở lại lần khám', () => {
  it('mỗi trạng thái có đúng nút: BOOKED bắt đầu khám, IN_PROGRESS tiếp tục, COMPLETED xem, CANCELLED không có', async () => {
    const wrapper = await mountView([row(1, 'BOOKED'), row(2, 'IN_PROGRESS'), row(3, 'COMPLETED'), row(4, 'CANCELLED')])

    expect(button(wrapper, 1).text()).toBe('Bắt đầu khám')
    expect(button(wrapper, 2).text()).toBe('Tiếp tục khám')
    expect(button(wrapper, 3).text()).toBe('Xem lần khám')
    expect(button(wrapper, 4)?.exists()).toBeFalsy()
  })

  it('Tiếp tục khám: tra lần khám theo lịch hẹn rồi vào màn hình khám', async () => {
    encountersApi.list.mockResolvedValue({ content: [{ id: 9, appointmentId: 2 }] })
    const wrapper = await mountView([row(2, 'IN_PROGRESS')])

    await button(wrapper, 2).trigger('click')
    await flushPromises()

    expect(encountersApi.list).toHaveBeenCalledWith({ appointmentId: 2 })
    expect(hoisted.push).toHaveBeenCalledWith('/doctor/encounters/9')
    expect(encountersApi.create).not.toHaveBeenCalled()
  })

  it('không tìm thấy lần khám của lịch hẹn thì báo lỗi, không chuyển trang', async () => {
    encountersApi.list.mockResolvedValue({ content: [] })
    const wrapper = await mountView([row(3, 'COMPLETED')])

    await button(wrapper, 3).trigger('click')
    await flushPromises()

    expect(hoisted.push).not.toHaveBeenCalled()
    expect(hoisted.toastError).toHaveBeenCalledWith('Không tìm thấy lần khám của lịch hẹn này.')
  })

  it('Bắt đầu khám tạo lần khám mới rồi vào màn hình khám', async () => {
    encountersApi.create.mockResolvedValue({ id: 12 })
    const wrapper = await mountView([row(1, 'BOOKED')])

    await button(wrapper, 1).trigger('click')
    await flushPromises()

    expect(encountersApi.create).toHaveBeenCalledWith({ appointmentId: 1 })
    expect(hoisted.push).toHaveBeenCalledWith('/doctor/encounters/12')
  })
})
