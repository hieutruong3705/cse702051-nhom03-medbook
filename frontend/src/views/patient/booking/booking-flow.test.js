import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError } from '@/api/http'
import { appointmentsApi } from '@/api/appointments'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { slotsApi } from '@/api/slots'
import BookingView from './BookingView.vue'
import SlotPicker from './SlotPicker.vue'
import { todayIso } from './bookingUtils'
import { SLOT_TAKEN_MESSAGE, useBookingFlow } from './useBookingFlow'

const hoisted = vi.hoisted(() => ({ route: { query: {}, params: {} }, push: vi.fn(), replace: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => hoisted.route,
    useRouter: () => ({ push: hoisted.push, replace: hoisted.replace, resolve: () => ({ matched: [] }) })
  }
})

vi.mock('@/api/appointments', () => ({ appointmentsApi: { create: vi.fn() } }))
vi.mock('@/api/doctors', () => ({ doctorsApi: { list: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/slots', () => ({ slotsApi: { availableByDoctor: vi.fn() } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { services: { list: vi.fn() } } }))

const addDays = (days) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return todayIso(date)
}

const DOCTORS = [
  { id: 1, fullName: 'BS. Nguyễn Văn A', specialty: { name: 'Nội tổng quát' } },
  { id: 2, fullName: 'BS. Trần Thị B', specialty: { name: 'Nhi khoa' } }
]
const SERVICES = [{ id: 10, name: 'Khám tổng quát', price: 150000, durationMinutes: 30 }]
const SLOTS = [
  { id: 101, slotDate: addDays(3), startTime: '09:00:00', endTime: '09:30:00' },
  { id: 102, slotDate: addDays(3), startTime: '09:30:00', endTime: '10:00:00' }
]

const conflict = (message = 'Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác!') =>
  new ApiRequestError({ status: 409, code: 'CONFLICT', message })

beforeEach(() => {
  vi.clearAllMocks()
  hoisted.route.query = {}
  doctorsApi.list.mockResolvedValue({ content: DOCTORS })
  catalogApi.services.list.mockResolvedValue({ content: SERVICES })
  slotsApi.availableByDoctor.mockResolvedValue(SLOTS)
})

/** Đưa luồng tới bước xác nhận với đủ lựa chọn. */
function readyFlow() {
  const flow = useBookingFlow()
  flow.selectDoctor(DOCTORS[0])
  flow.next()
  flow.setDate(addDays(3))
  flow.next()
  flow.selectSlot({ id: 101, date: addDays(3), startTime: '09:00:00', endTime: '09:30:00' })
  flow.next()
  flow.service.value = SERVICES[0]
  flow.notes.value = '  Đau đầu  '
  flow.next()
  return flow
}

describe('useBookingFlow', () => {
  it('chỉ cho tiếp tục khi bước hiện tại đã hợp lệ', () => {
    const flow = useBookingFlow()
    expect(flow.canContinue.value).toBe(false)
    expect(flow.currentProblem.value).toBe('Vui lòng chọn bác sĩ')

    flow.selectDoctor(DOCTORS[0])
    expect(flow.next()).toBe(true)
    expect(flow.step.value).toBe(2)

    flow.setDate(addDays(-1))
    expect(flow.canContinue.value).toBe(false)
    expect(flow.currentProblem.value).toBe('Ngày khám phải từ hôm nay trở đi')
    flow.setDate(addDays(2))
    expect(flow.canContinue.value).toBe(true)
  })

  it('quay lại không làm mất lựa chọn; đổi bác sĩ hoặc ngày thì bỏ khung giờ đã chọn', () => {
    const flow = readyFlow()
    expect(flow.step.value).toBe(5)

    flow.back()
    flow.back()
    expect(flow.step.value).toBe(3)
    expect(flow.slot.value.id).toBe(101)
    expect(flow.service.value.id).toBe(10)
    expect(flow.notes.value).toContain('Đau đầu')

    flow.setDate(addDays(4))
    expect(flow.slot.value).toBeNull()
    flow.selectSlot({ id: 102 })
    flow.selectDoctor(DOCTORS[1])
    expect(flow.slot.value).toBeNull()
    flow.selectDoctor(DOCTORS[1]) // chọn lại cùng bác sĩ không làm mất slot mới chọn
    flow.selectSlot({ id: 102 })
    flow.selectDoctor(DOCTORS[1])
    expect(flow.slot.value.id).toBe(102)
  })

  it('không nhảy tới bước chưa từng đi qua', () => {
    const flow = useBookingFlow()
    flow.goTo(4)
    expect(flow.step.value).toBe(1)
  })

  it('đặt thành công: gửi đúng dữ liệu và sang bước hoàn tất', async () => {
    appointmentsApi.create.mockResolvedValue({ id: 55, doctorName: 'BS. Nguyễn Văn A' })
    const flow = readyFlow()

    await flow.submit()

    expect(appointmentsApi.create).toHaveBeenCalledWith({ slotId: 101, serviceId: 10, notes: 'Đau đầu' })
    expect(flow.confirmed.value.id).toBe(55)
    expect(flow.step.value).toBe(6)
  })

  it('chống gửi lặp: bấm xác nhận nhiều lần khi đang gửi chỉ tạo MỘT yêu cầu', async () => {
    let resolveCreate
    appointmentsApi.create.mockReturnValue(new Promise((resolve) => (resolveCreate = resolve)))
    const flow = readyFlow()

    const first = flow.submit()
    const second = flow.submit()
    const third = flow.submit()
    expect(flow.submitting.value).toBe(true)
    resolveCreate({ id: 7 })
    await Promise.all([first, second, third])

    expect(appointmentsApi.create).toHaveBeenCalledTimes(1)
    // đặt xong rồi thì bấm tiếp cũng không tạo lịch thứ hai
    await flow.submit()
    expect(appointmentsApi.create).toHaveBeenCalledTimes(1)
  })

  it('409: về bước chọn giờ, bỏ khung giờ cũ, yêu cầu tải lại, GIỮ NGUYÊN các lựa chọn khác', async () => {
    appointmentsApi.create.mockRejectedValue(conflict())
    const flow = readyFlow()
    const refreshBefore = flow.slotsRefreshKey.value

    const result = await flow.submit()

    expect(result).toBeNull()
    expect(flow.step.value).toBe(3)
    expect(flow.conflictMessage.value).toContain('vừa được người khác đặt')
    expect(flow.slot.value).toBeNull()
    expect(flow.slotsRefreshKey.value).toBe(refreshBefore + 1)
    expect(flow.doctor.value.id).toBe(1)
    expect(flow.date.value).toBe(addDays(3))
    expect(flow.service.value.id).toBe(10)
    expect(flow.notes.value).toContain('Đau đầu')
    expect(flow.submitting.value).toBe(false)
  })

  it('409 không kèm thông điệp dùng thông điệp mặc định', async () => {
    appointmentsApi.create.mockRejectedValue(new ApiRequestError({ status: 409, code: 'CONFLICT' }))
    const flow = readyFlow()
    await flow.submit()
    // ApiRequestError có thông điệp mặc định của http.js; chỉ cần đã quay về bước chọn giờ với thông báo
    expect(flow.step.value).toBe(3)
    expect(flow.conflictMessage.value).not.toBe('')
    expect(SLOT_TAKEN_MESSAGE).toContain('Khung giờ vừa được người khác đặt')
  })

  it('400 kèm details: hiện lỗi đúng trường và quay về bước chứa trường đó', async () => {
    appointmentsApi.create.mockRejectedValue(
      new ApiRequestError({ status: 400, code: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ', details: { serviceId: 'Dịch vụ khám đã ngừng sử dụng' } })
    )
    const flow = readyFlow()
    await flow.submit()
    expect(flow.step.value).toBe(4)
    expect(flow.fieldErrors.serviceId).toBe('Dịch vụ khám đã ngừng sử dụng')
  })

  it('lỗi máy chủ/mạng: ở lại bước xác nhận với thông báo, vẫn thử lại được', async () => {
    appointmentsApi.create.mockRejectedValueOnce(new ApiRequestError({ status: 500, code: 'INTERNAL_ERROR' }))
    appointmentsApi.create.mockResolvedValueOnce({ id: 9 })
    const flow = readyFlow()

    await flow.submit()
    expect(flow.step.value).toBe(5)
    expect(flow.submitError.value).toBe('Hệ thống đang bận, vui lòng thử lại sau.')

    await flow.submit()
    expect(flow.step.value).toBe(6)
  })
})

describe('SlotPicker', () => {
  it('tải khung giờ của bác sĩ theo ngày và phát sự kiện khi chọn', async () => {
    const wrapper = mount(SlotPicker, { props: { doctorId: 1, date: addDays(3) } })
    await flushPromises()

    expect(slotsApi.availableByDoctor).toHaveBeenCalledWith(1, addDays(3))
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('09:00 – 09:30')

    await buttons[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')[0]).toEqual([102])
    expect(wrapper.emitted('select')[0][0]).toMatchObject({ id: 102, startTime: '09:30:00' })
  })

  it('không gọi API với ngày đã qua và báo cho người dùng', async () => {
    const wrapper = mount(SlotPicker, { props: { doctorId: 1, date: addDays(-2) } })
    await flushPromises()
    expect(slotsApi.availableByDoctor).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Ngày khám đã qua')
  })

  it('hiện trạng thái rỗng và lỗi có nút thử lại', async () => {
    slotsApi.availableByDoctor.mockResolvedValueOnce([])
    const empty = mount(SlotPicker, { props: { doctorId: 1, date: addDays(3) } })
    await flushPromises()
    expect(empty.text()).toContain('Không còn khung giờ trống')

    slotsApi.availableByDoctor.mockRejectedValueOnce(new ApiRequestError({ status: 500, code: 'INTERNAL_ERROR' }))
    const failed = mount(SlotPicker, { props: { doctorId: 1, date: addDays(3) } })
    await flushPromises()
    expect(failed.find('[role="alert"]').text()).toContain('Hệ thống đang bận')
    await failed.find('button').trigger('click')
    await flushPromises()
    expect(failed.findAll('button')).toHaveLength(2)
  })

  it('bỏ kết quả cũ khi đã đổi sang bác sĩ khác (không hiện nhầm khung giờ)', async () => {
    let resolveFirst
    slotsApi.availableByDoctor.mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)))
    slotsApi.availableByDoctor.mockResolvedValueOnce([{ id: 900, slotDate: addDays(3), startTime: '14:00:00', endTime: '14:30:00' }])

    const wrapper = mount(SlotPicker, { props: { doctorId: 1, date: addDays(3) } })
    await wrapper.setProps({ doctorId: 2 })
    await flushPromises()
    resolveFirst(SLOTS) // phản hồi chậm của bác sĩ 1 đến sau
    await flushPromises()

    const labels = wrapper.findAll('button').map((button) => button.text())
    expect(labels).toEqual(['14:00 – 14:30'])
  })

  it('tải lại khi refreshKey đổi và bỏ lựa chọn nếu slot không còn trong danh sách', async () => {
    const wrapper = mount(SlotPicker, { props: { doctorId: 1, date: addDays(3), modelValue: 101 } })
    await flushPromises()
    slotsApi.availableByDoctor.mockResolvedValueOnce([SLOTS[1]])

    await wrapper.setProps({ refreshKey: 1 })
    await flushPromises()

    expect(slotsApi.availableByDoctor).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([null])
  })
})

describe('BookingView', () => {
  const mountView = () => mount(BookingView, { attachTo: document.body })

  async function pickDoctorDateSlot(wrapper) {
    await wrapper.findAll('ul[aria-label="Danh sách bác sĩ"] button')[0].trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục').trigger('click')
    await wrapper.find('#booking-date').setValue(addDays(3))
    await wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục').trigger('click')
    await flushPromises()
    await wrapper.find('ul[aria-label="Khung giờ còn trống"] button').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục').trigger('click')
    await flushPromises()
    await wrapper.find('#booking-service').setValue('10')
    await wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục').trigger('click')
  }

  it('liệt kê bác sĩ, lọc theo từ khóa và chỉ cho tiếp tục sau khi chọn', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('BS. Nguyễn Văn A')
    expect(wrapper.text()).toContain('BS. Trần Thị B')
    const next = () => wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục')
    expect(next().attributes('disabled')).toBeDefined()

    await wrapper.find('#booking-doctor-search').setValue('nhi')
    expect(wrapper.text()).not.toContain('BS. Nguyễn Văn A')
    expect(wrapper.text()).toContain('BS. Trần Thị B')

    await wrapper.findAll('ul[aria-label="Danh sách bác sĩ"] button')[0].trigger('click')
    expect(next().attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('?doctorId=2 chọn sẵn bác sĩ và chuyển thẳng sang bước chọn ngày', async () => {
    hoisted.route.query = { doctorId: '2' }
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('#booking-step-title').text()).toContain('Bước 2')
    expect(wrapper.find('#booking-date').exists()).toBe(true)
    wrapper.unmount()
  })

  it('luồng đầy đủ tới xác nhận rồi đặt thành công, hiện mã lịch hẹn và liên kết chi tiết', async () => {
    appointmentsApi.create.mockResolvedValue({ id: 77, doctorName: 'BS. Nguyễn Văn A', date: addDays(3), startTime: '09:00:00', endTime: '09:30:00' })
    const wrapper = mountView()
    await flushPromises()
    await pickDoctorDateSlot(wrapper)

    expect(wrapper.find('[data-test="summary"]').text()).toContain('Khám tổng quát')
    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(appointmentsApi.create).toHaveBeenCalledWith({ slotId: 101, serviceId: 10, notes: undefined })
    expect(wrapper.find('[data-test="done"]').text()).toContain('#77')
    wrapper.unmount()
  })

  it('409: hiện thông báo, tải lại danh sách giờ và giữ nguyên bác sĩ/ngày/dịch vụ', async () => {
    appointmentsApi.create.mockRejectedValue(conflict())
    const wrapper = mountView()
    await flushPromises()
    await pickDoctorDateSlot(wrapper)
    const loadsBefore = slotsApi.availableByDoctor.mock.calls.length

    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="conflict"]').text()).toContain('vừa được người khác đặt')
    expect(wrapper.find('#booking-step-title').text()).toContain('Bước 3')
    expect(slotsApi.availableByDoctor.mock.calls.length).toBe(loadsBefore + 1)
    expect(slotsApi.availableByDoctor).toHaveBeenLastCalledWith(1, addDays(3))
    // chưa chọn lại giờ nên chưa thể tiếp tục; chọn giờ khác thì đi tiếp được
    const next = () => wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục')
    expect(next().attributes('disabled')).toBeDefined()
    await wrapper.findAll('ul[aria-label="Khung giờ còn trống"] button')[1].trigger('click')
    expect(next().attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('nút xác nhận khóa khi đang gửi: bấm hai lần chỉ gửi một yêu cầu', async () => {
    let resolveCreate
    appointmentsApi.create.mockReturnValue(new Promise((resolve) => (resolveCreate = resolve)))
    const wrapper = mountView()
    await flushPromises()
    await pickDoctorDateSlot(wrapper)

    const confirm = () => wrapper.findAll('button').find((button) => button.text().includes('Xác nhận đặt lịch'))
    await confirm().trigger('click')
    await confirm().trigger('click')
    expect(confirm().attributes('disabled')).toBeDefined()
    expect(appointmentsApi.create).toHaveBeenCalledTimes(1)

    resolveCreate({ id: 1 })
    await flushPromises()
    wrapper.unmount()
  })
})
