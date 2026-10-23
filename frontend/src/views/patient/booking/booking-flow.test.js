import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { ApiRequestError } from '@/api/http'
import { appointmentsApi } from '@/api/appointments'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { slotsApi } from '@/api/slots'
import BookingView from './BookingView.vue'
import SlotPicker from './SlotPicker.vue'
import { todayIso } from './bookingUtils'
import { HOLD_EXPIRED_MESSAGE, SLOT_TAKEN_MESSAGE, useBookingFlow } from './useBookingFlow'
import { formatHoldCountdown, useSlotHold } from './useSlotHold'

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
vi.mock('@/api/slots', () => ({
  slotsApi: { availableByDoctor: vi.fn(), earliest: vi.fn(), hold: vi.fn(), release: vi.fn() }
}))
vi.mock('@/api/catalog', () => ({ catalogApi: { services: { list: vi.fn() }, specialties: { list: vi.fn() } } }))

const addDays = (days) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return todayIso(date)
}

const DOCTORS = [
  { id: 1, fullName: 'BS. Nguyễn Văn A', specialty: { name: 'Nội tổng quát' } },
  { id: 2, fullName: 'BS. Trần Thị B', specialty: { name: 'Nhi khoa' } }
]
const SERVICES = [
  { id: 10, name: 'Khám tổng quát', price: 150000, durationMinutes: 30 },
  { id: 11, name: 'Khám chuyên khoa', price: 300000, durationMinutes: 45 }
]
const SPECIALTIES = [{ id: 5, name: 'Nội tổng quát' }, { id: 6, name: 'Nhi khoa' }]
const SLOTS = [
  { id: 101, slotDate: addDays(3), startTime: '09:00:00', endTime: '09:30:00' },
  { id: 102, slotDate: addDays(3), startTime: '09:30:00', endTime: '10:00:00' }
]
const EARLIEST = [
  { slotId: 301, doctorId: 2, doctorName: 'BS. Trần Thị B', specialtyId: 6, specialtyName: 'Nhi khoa', date: addDays(1), startTime: '08:00:00', endTime: '08:30:00' },
  { slotId: 302, doctorId: 1, doctorName: 'BS. Nguyễn Văn A', specialtyId: 5, specialtyName: 'Nội tổng quát', date: addDays(2), startTime: '14:00:00', endTime: '14:30:00' }
]
const SLOT_101 = { id: 101, date: addDays(3), startTime: '09:00:00', endTime: '09:30:00' }

const conflict = (message = 'Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác!') =>
  new ApiRequestError({ status: 409, code: 'CONFLICT', message })
const gone = () =>
  new ApiRequestError({ status: 410, code: 'GONE', message: 'Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ!' })
/** Máy chủ giữ được khung giờ: mã bản giữ suy ra từ mã khung giờ để test đối chiếu được. */
const heldFor = (slotId, remainingSeconds = 600) => ({ holdId: 5000 + slotId, slotId, expiresAt: '2030-01-01T08:10:00', remainingSeconds })

const scopes = []
/** Chạy composable trong một effect scope để bộ đếm giờ của nó được dọn sau mỗi test. */
function inScope(factory) {
  const scope = effectScope()
  scopes.push(scope)
  return scope.run(factory)
}

beforeEach(() => {
  vi.clearAllMocks()
  hoisted.route.query = {}
  doctorsApi.list.mockResolvedValue({ content: DOCTORS })
  catalogApi.services.list.mockResolvedValue({ content: SERVICES })
  catalogApi.specialties.list.mockResolvedValue({ content: SPECIALTIES })
  slotsApi.availableByDoctor.mockResolvedValue(SLOTS)
  slotsApi.earliest.mockResolvedValue(EARLIEST)
  slotsApi.hold.mockImplementation((slotId) => Promise.resolve(heldFor(slotId)))
  slotsApi.release.mockResolvedValue(null)
})

afterEach(() => {
  scopes.splice(0).forEach((scope) => scope.stop())
  vi.useRealTimers()
})

/** Đưa luồng tới bước xác nhận với đủ lựa chọn và một bản giữ chỗ còn hạn. */
async function readyFlow() {
  const flow = inScope(() => useBookingFlow())
  flow.selectService(SERVICES[0])
  flow.next()
  flow.selectDoctor(DOCTORS[0])
  flow.next()
  flow.setDate(addDays(3))
  flow.next()
  await flow.selectSlot(SLOT_101)
  flow.next()
  flow.notes.value = '  Đau đầu  '
  return flow
}

describe('useBookingFlow', () => {
  it('bắt đầu bằng dịch vụ và chỉ cho tiếp tục khi bước hiện tại đã hợp lệ', () => {
    const flow = inScope(() => useBookingFlow())
    expect(flow.canContinue.value).toBe(false)
    expect(flow.currentProblem.value).toBe('Vui lòng chọn dịch vụ khám')

    flow.selectService(SERVICES[0])
    expect(flow.next()).toBe(true)
    expect(flow.currentProblem.value).toBe('Vui lòng chọn bác sĩ')
    flow.selectDoctor(DOCTORS[0])
    expect(flow.next()).toBe(true)
    expect(flow.step.value).toBe(3)

    flow.setDate(addDays(-1))
    expect(flow.canContinue.value).toBe(false)
    expect(flow.currentProblem.value).toBe('Ngày khám phải từ hôm nay trở đi')
    flow.setDate(addDays(2))
    expect(flow.canContinue.value).toBe(true)
  })

  it('quay lại không làm mất lựa chọn; đổi dịch vụ, bác sĩ hoặc ngày thì bỏ khung giờ đã chọn và nhả bản giữ', async () => {
    const flow = await readyFlow()
    expect(flow.step.value).toBe(5)

    flow.back()
    expect(flow.step.value).toBe(4)
    expect(flow.slot.value.id).toBe(101)
    expect(flow.service.value.id).toBe(10)
    expect(flow.notes.value).toContain('Đau đầu')
    expect(flow.hold.value.holdId).toBe(5101)

    flow.setDate(addDays(4))
    expect(flow.slot.value).toBeNull()
    expect(flow.hold.value).toBeNull()
    await flushPromises()
    expect(slotsApi.release).toHaveBeenCalledWith(101)

    await flow.selectSlot({ id: 102 })
    flow.selectDoctor(DOCTORS[1])
    expect(flow.slot.value).toBeNull()

    await flow.selectSlot({ id: 102 })
    flow.selectDoctor(DOCTORS[1]) // chọn lại cùng bác sĩ không làm mất khung giờ mới chọn
    expect(flow.slot.value.id).toBe(102)
    flow.selectService(SERVICES[0]) // chọn lại cùng dịch vụ cũng vậy
    expect(flow.slot.value.id).toBe(102)
    flow.selectService(SERVICES[1])
    expect(flow.slot.value).toBeNull()
    expect(flow.hold.value).toBeNull()
  })

  it('không nhảy tới bước chưa từng đi qua', () => {
    const flow = inScope(() => useBookingFlow())
    flow.goTo(4)
    expect(flow.step.value).toBe(1)
  })

  it('Chọn khung giờ thì giữ chỗ đúng khung giờ đó và có đồng hồ đếm ngược; chọn khung giờ khác thì giữ cái mới', async () => {
    const flow = inScope(() => useBookingFlow())
    flow.selectService(SERVICES[0])
    flow.selectDoctor(DOCTORS[0])
    flow.setDate(addDays(3))

    expect(await flow.selectSlot(SLOT_101)).toBe(true)

    expect(slotsApi.hold).toHaveBeenCalledTimes(1)
    expect(slotsApi.hold).toHaveBeenCalledWith(101)
    expect(flow.hold.value).toEqual({ holdId: 5101, slotId: 101, expiresAt: '2030-01-01T08:10:00' })
    expect(flow.remainingSeconds.value).toBe(600)

    expect(await flow.selectSlot({ id: 102 })).toBe(true)
    expect(slotsApi.hold).toHaveBeenLastCalledWith(102)
    expect(flow.hold.value.holdId).toBe(5102)
    expect(flow.slot.value.id).toBe(102)
  })

  it('không giữ được khung giờ (người khác vừa giữ): bỏ lựa chọn, báo lý do của máy chủ và yêu cầu tải lại danh sách', async () => {
    slotsApi.hold.mockRejectedValueOnce(conflict('Khung giờ đang được người khác giữ chỗ. Vui lòng chọn khung giờ khác!'))
    const flow = inScope(() => useBookingFlow())
    flow.selectService(SERVICES[0])
    flow.next()
    flow.selectDoctor(DOCTORS[0])
    flow.next()
    flow.setDate(addDays(3))
    flow.next()
    const refreshBefore = flow.slotsRefreshKey.value

    expect(await flow.selectSlot(SLOT_101)).toBe(false)

    expect(flow.slot.value).toBeNull()
    expect(flow.hold.value).toBeNull()
    expect(flow.conflictMessage.value).toContain('đang được người khác giữ chỗ')
    expect(flow.slotsRefreshKey.value).toBe(refreshBefore + 1)
    expect(flow.step.value).toBe(4)
    expect(flow.canContinue.value).toBe(false)
  })

  it('Hết thời gian giữ chỗ: bỏ khung giờ, báo, tải lại danh sách và quay về bước chọn giờ', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    slotsApi.hold.mockImplementation((slotId) => Promise.resolve(heldFor(slotId, 3)))
    const flow = await readyFlow()
    const refreshBefore = flow.slotsRefreshKey.value
    expect(flow.step.value).toBe(5)
    expect(flow.remainingSeconds.value).toBe(3)

    vi.advanceTimersByTime(2000)
    expect(flow.remainingSeconds.value).toBe(1)
    expect(flow.slot.value.id).toBe(101)

    vi.advanceTimersByTime(1000)
    await flushPromises()

    expect(flow.hold.value).toBeNull()
    expect(flow.slot.value).toBeNull()
    expect(flow.conflictMessage.value).toBe(HOLD_EXPIRED_MESSAGE)
    expect(flow.slotsRefreshKey.value).toBe(refreshBefore + 1)
    expect(flow.step.value).toBe(4)
    expect(flow.service.value.id).toBe(10)
    expect(flow.doctor.value.id).toBe(1)
    expect(flow.date.value).toBe(addDays(3))
  })

  it('đặt thành công: gửi đúng dữ liệu kèm holdId và sang bước hoàn tất, không gọi nhả chỗ', async () => {
    appointmentsApi.create.mockResolvedValue({ id: 55, doctorName: 'BS. Nguyễn Văn A' })
    const flow = await readyFlow()

    await flow.submit()

    expect(appointmentsApi.create).toHaveBeenCalledWith({ slotId: 101, serviceId: 10, notes: 'Đau đầu', holdId: 5101 })
    expect(flow.confirmed.value.id).toBe(55)
    expect(flow.step.value).toBe(6)
    expect(flow.hold.value).toBeNull()
    await flushPromises()
    expect(slotsApi.release).not.toHaveBeenCalled()
  })

  it('chống gửi lặp: bấm xác nhận nhiều lần khi đang gửi chỉ tạo MỘT yêu cầu', async () => {
    let resolveCreate
    appointmentsApi.create.mockReturnValue(new Promise((resolve) => (resolveCreate = resolve)))
    const flow = await readyFlow()

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
    const flow = await readyFlow()
    const refreshBefore = flow.slotsRefreshKey.value

    const result = await flow.submit()

    expect(result).toBeNull()
    expect(flow.step.value).toBe(4)
    expect(flow.conflictMessage.value).toContain('vừa được người khác đặt')
    expect(flow.slot.value).toBeNull()
    expect(flow.slotsRefreshKey.value).toBe(refreshBefore + 1)
    expect(flow.doctor.value.id).toBe(1)
    expect(flow.date.value).toBe(addDays(3))
    expect(flow.service.value.id).toBe(10)
    expect(flow.notes.value).toContain('Đau đầu')
    expect(flow.submitting.value).toBe(false)
  })

  it('410 khi xác nhận (bản giữ chỗ đã hết hạn): về bước chọn giờ với thông điệp của máy chủ, giữ nguyên các lựa chọn khác', async () => {
    appointmentsApi.create.mockRejectedValue(gone())
    const flow = await readyFlow()
    const refreshBefore = flow.slotsRefreshKey.value

    expect(await flow.submit()).toBeNull()

    expect(flow.step.value).toBe(4)
    expect(flow.conflictMessage.value).toBe('Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ!')
    expect(flow.slot.value).toBeNull()
    expect(flow.hold.value).toBeNull()
    expect(flow.slotsRefreshKey.value).toBe(refreshBefore + 1)
    expect(flow.service.value.id).toBe(10)
    expect(flow.doctor.value.id).toBe(1)
    expect(flow.date.value).toBe(addDays(3))
    expect(flow.notes.value).toContain('Đau đầu')
  })

  it('422 (dịch vụ dài hơn khung giờ): về bước chọn giờ và hiện đúng thông điệp của máy chủ', async () => {
    appointmentsApi.create.mockRejectedValue(
      new ApiRequestError({ status: 422, code: 'UNPROCESSABLE_ENTITY', message: 'Dịch vụ cần 45 phút, khung giờ này chỉ dài 30 phút' })
    )
    const flow = await readyFlow()

    await flow.submit()

    expect(flow.step.value).toBe(4)
    expect(flow.conflictMessage.value).toBe('Dịch vụ cần 45 phút, khung giờ này chỉ dài 30 phút')
    expect(flow.slot.value).toBeNull()
    expect(flow.service.value.id).toBe(10)
  })

  it('409 không kèm thông điệp dùng thông điệp mặc định', async () => {
    appointmentsApi.create.mockRejectedValue(new ApiRequestError({ status: 409, code: 'CONFLICT' }))
    const flow = await readyFlow()
    await flow.submit()
    // ApiRequestError có thông điệp mặc định của http.js; chỉ cần đã quay về bước chọn giờ với thông báo
    expect(flow.step.value).toBe(4)
    expect(flow.conflictMessage.value).not.toBe('')
    expect(SLOT_TAKEN_MESSAGE).toContain('Khung giờ vừa được người khác đặt')
  })

  it('400 kèm details: hiện lỗi đúng trường và quay về bước chứa trường đó', async () => {
    appointmentsApi.create.mockRejectedValue(
      new ApiRequestError({ status: 400, code: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ', details: { serviceId: 'Dịch vụ khám đã ngừng sử dụng' } })
    )
    const flow = await readyFlow()
    await flow.submit()
    expect(flow.step.value).toBe(1)
    expect(flow.fieldErrors.serviceId).toBe('Dịch vụ khám đã ngừng sử dụng')
  })

  it('lỗi máy chủ/mạng: ở lại bước xác nhận với thông báo, vẫn thử lại được', async () => {
    appointmentsApi.create.mockRejectedValueOnce(new ApiRequestError({ status: 500, code: 'INTERNAL_ERROR' }))
    appointmentsApi.create.mockResolvedValueOnce({ id: 9 })
    const flow = await readyFlow()

    await flow.submit()
    expect(flow.step.value).toBe(5)
    expect(flow.submitError.value).toBe('Hệ thống đang bận, vui lòng thử lại sau.')
    expect(flow.hold.value.holdId).toBe(5101)

    await flow.submit()
    expect(flow.step.value).toBe(6)
  })

  it('Chọn một dòng giờ sớm nhất: điền bác sĩ, ngày, khung giờ, giữ chỗ và tới thẳng bước xác nhận', async () => {
    const flow = inScope(() => useBookingFlow())
    flow.selectService(SERVICES[0])

    const ok = await flow.chooseEarliest({
      doctor: DOCTORS[1],
      date: addDays(1),
      slot: { id: 301, date: addDays(1), startTime: '08:00:00', endTime: '08:30:00' }
    })

    expect(ok).toBe(true)
    expect(slotsApi.hold).toHaveBeenCalledWith(301)
    expect(flow.step.value).toBe(5)
    expect(flow.furthest.value).toBe(5)
    expect(flow.doctor.value.id).toBe(2)
    expect(flow.date.value).toBe(addDays(1))
    expect(flow.slot.value.id).toBe(301)
    expect(flow.hold.value.holdId).toBe(5301)
    expect(flow.canContinue.value).toBe(true)
  })
})

describe('useSlotHold', () => {
  it('định dạng số giây còn lại thành mm:ss', () => {
    expect(formatHoldCountdown(600)).toBe('10:00')
    expect(formatHoldCountdown(598)).toBe('09:58')
    expect(formatHoldCountdown(7)).toBe('00:07')
    expect(formatHoldCountdown(-3)).toBe('00:00')
    expect(formatHoldCountdown(undefined)).toBe('00:00')
  })

  it('đếm ngược theo số giây máy chủ trả về; về 0 thì hold thành null và expired thành true', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] })
    slotsApi.hold.mockResolvedValue(heldFor(7, 2))
    const state = inScope(() => useSlotHold())

    const held = await state.holdSlot(7)

    expect(held).toEqual({ holdId: 5007, slotId: 7, expiresAt: '2030-01-01T08:10:00' })
    expect(state.remainingSeconds.value).toBe(2)
    expect(state.expired.value).toBe(false)
    vi.advanceTimersByTime(1000)
    expect(state.remainingSeconds.value).toBe(1)
    vi.advanceTimersByTime(1000)
    expect(state.remainingSeconds.value).toBe(0)
    expect(state.hold.value).toBeNull()
    expect(state.expired.value).toBe(true)
  })

  it('giữ thất bại: trả null, holdError có thông điệp của máy chủ, bản giữ cũ được nhả', async () => {
    const state = inScope(() => useSlotHold())
    await state.holdSlot(7)
    slotsApi.hold.mockRejectedValueOnce(conflict('Khung giờ đang được người khác giữ chỗ. Vui lòng chọn khung giờ khác!'))

    expect(await state.holdSlot(8)).toBeNull()

    expect(state.holdError.value).toContain('đang được người khác giữ chỗ')
    expect(state.hold.value).toBeNull()
    expect(state.holding.value).toBe(false)
    expect(slotsApi.release).toHaveBeenCalledWith(7)
  })

  it('bấm liên tiếp hai khung giờ: yêu cầu gửi lần lượt và bản giữ cuối cùng khớp lựa chọn cuối', async () => {
    const pending = []
    slotsApi.hold.mockImplementation((slotId) => new Promise((resolve) => pending.push(() => resolve(heldFor(slotId)))))
    const state = inScope(() => useSlotHold())

    const first = state.holdSlot(7)
    await flushPromises() // yêu cầu thứ nhất đã gửi đi và đang chờ máy chủ
    const second = state.holdSlot(8)
    await flushPromises()
    expect(slotsApi.hold).toHaveBeenCalledTimes(1) // yêu cầu thứ hai chờ yêu cầu thứ nhất xong
    expect(slotsApi.hold).toHaveBeenLastCalledWith(7)
    expect(state.holding.value).toBe(true)

    pending.shift()()
    await flushPromises()
    expect(slotsApi.release).toHaveBeenCalledWith(7) // bản giữ đến trễ cho lựa chọn cũ được nhả ngay
    expect(slotsApi.hold).toHaveBeenLastCalledWith(8)
    pending.shift()()

    expect(await first).toBeNull()
    expect((await second).holdId).toBe(5008)
    expect(state.hold.value.slotId).toBe(8)
    expect(state.holding.value).toBe(false)
  })

  it('bấm khung giờ khác trước khi yêu cầu đầu kịp gửi: chỉ gửi yêu cầu cho lựa chọn cuối', async () => {
    const state = inScope(() => useSlotHold())

    const first = state.holdSlot(7)
    const second = state.holdSlot(8)

    expect(await first).toBeNull()
    expect((await second).slotId).toBe(8)
    expect(slotsApi.hold).toHaveBeenCalledTimes(1)
    expect(slotsApi.hold).toHaveBeenCalledWith(8)
    expect(slotsApi.release).not.toHaveBeenCalled()
  })

  it('releaseHold nhả bản giữ trên máy chủ; chưa giữ gì thì không gọi; component bị gỡ thì tự nhả', async () => {
    const scope = effectScope()
    const state = scope.run(() => useSlotHold())
    await state.releaseHold()
    expect(slotsApi.release).not.toHaveBeenCalled()

    await state.holdSlot(7)
    await state.releaseHold()
    expect(slotsApi.release).toHaveBeenCalledWith(7)
    expect(state.hold.value).toBeNull()
    expect(state.remainingSeconds.value).toBe(0)

    await state.holdSlot(9)
    scope.stop()
    await flushPromises()
    expect(slotsApi.release).toHaveBeenLastCalledWith(9)
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

  it('Có serviceId thì truyền xuống API và tải lại khi dịch vụ đổi; không có thì gọi như cũ', async () => {
    const wrapper = mount(SlotPicker, { props: { doctorId: 1, date: addDays(3), serviceId: 10 } })
    await flushPromises()
    expect(slotsApi.availableByDoctor).toHaveBeenLastCalledWith(1, addDays(3), 10)

    await wrapper.setProps({ serviceId: 11 })
    await flushPromises()
    expect(slotsApi.availableByDoctor).toHaveBeenCalledTimes(2)
    expect(slotsApi.availableByDoctor).toHaveBeenLastCalledWith(1, addDays(3), 11)

    slotsApi.availableByDoctor.mockResolvedValueOnce([])
    await wrapper.setProps({ serviceId: 10 })
    await flushPromises()
    expect(wrapper.text()).toContain('Không có khung giờ nào đủ dài cho dịch vụ đã chọn')
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
  const next = (wrapper) => wrapper.findAll('button').find((button) => button.text() === 'Tiếp tục')

  async function pickService(wrapper) {
    await wrapper.find('#booking-service').setValue('10')
    await flushPromises()
  }

  /** Dịch vụ → bác sĩ → ngày → khung giờ, dừng ở bước xác nhận. */
  async function pickUntilConfirm(wrapper) {
    await pickService(wrapper)
    await next(wrapper).trigger('click')
    await wrapper.findAll('ul[aria-label="Danh sách bác sĩ"] button')[0].trigger('click')
    await next(wrapper).trigger('click')
    await wrapper.find('#booking-date').setValue(addDays(3))
    await next(wrapper).trigger('click')
    await flushPromises()
    await wrapper.find('ul[aria-label="Khung giờ còn trống"] button').trigger('click')
    await flushPromises()
    await next(wrapper).trigger('click')
    await flushPromises()
  }

  it('Bước đầu là chọn dịch vụ; chưa chọn thì chưa đi tiếp được', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('#booking-step-title').text()).toBe('Bước 1: Chọn dịch vụ')
    expect(next(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="earliest"]').exists()).toBe(false)

    await pickService(wrapper)
    expect(wrapper.text()).toContain('khoảng 30 phút')
    expect(next(wrapper).attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('liệt kê bác sĩ, lọc theo từ khóa và chỉ cho tiếp tục sau khi chọn', async () => {
    const wrapper = mountView()
    await flushPromises()
    await pickService(wrapper)
    await next(wrapper).trigger('click')

    expect(wrapper.text()).toContain('BS. Nguyễn Văn A')
    expect(wrapper.text()).toContain('BS. Trần Thị B')
    expect(next(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.find('#booking-doctor-search').setValue('nhi')
    expect(wrapper.text()).not.toContain('BS. Nguyễn Văn A')
    expect(wrapper.text()).toContain('BS. Trần Thị B')

    await wrapper.findAll('ul[aria-label="Danh sách bác sĩ"] button')[0].trigger('click')
    expect(next(wrapper).attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('?doctorId=2 chọn sẵn bác sĩ: chọn xong dịch vụ thì chuyển thẳng sang bước chọn ngày', async () => {
    hoisted.route.query = { doctorId: '2' }
    const wrapper = mountView()
    await flushPromises()
    await pickService(wrapper)

    await next(wrapper).trigger('click')

    expect(wrapper.find('#booking-step-title').text()).toContain('Bước 3')
    expect(wrapper.find('#booking-date').exists()).toBe(true)
    await wrapper.findAll('button').find((button) => button.text() === 'Quay lại').trigger('click')
    expect(wrapper.find('ul[aria-label="Danh sách bác sĩ"] button[aria-pressed="true"]').text()).toContain('BS. Trần Thị B')
    wrapper.unmount()
  })

  it('Chọn khung giờ thì giữ chỗ và hiện đồng hồ đếm ngược đọc được bằng trình đọc màn hình', async () => {
    const wrapper = mountView()
    await flushPromises()
    await pickService(wrapper)
    await next(wrapper).trigger('click')
    await wrapper.findAll('ul[aria-label="Danh sách bác sĩ"] button')[0].trigger('click')
    await next(wrapper).trigger('click')
    await wrapper.find('#booking-date').setValue(addDays(3))
    await next(wrapper).trigger('click')
    await flushPromises()
    expect(slotsApi.availableByDoctor).toHaveBeenLastCalledWith(1, addDays(3), 10)
    expect(wrapper.find('[data-test="hold-countdown"]').exists()).toBe(false)

    await wrapper.find('ul[aria-label="Khung giờ còn trống"] button').trigger('click')
    await flushPromises()

    expect(slotsApi.hold).toHaveBeenCalledTimes(1)
    expect(slotsApi.hold).toHaveBeenCalledWith(101)
    const countdown = wrapper.find('[data-test="hold-countdown"]')
    expect(countdown.text()).toContain('Đang giữ chỗ, còn 10:00')
    expect(countdown.find('[aria-live="polite"]').text()).toBe('Đang giữ chỗ, còn khoảng 10 phút')

    wrapper.unmount()
    await flushPromises()
    expect(slotsApi.release).toHaveBeenCalledWith(101) // rời trang thì nhả chỗ
  })

  it('luồng đầy đủ tới xác nhận rồi đặt thành công, hiện mã lịch hẹn và liên kết chi tiết', async () => {
    appointmentsApi.create.mockResolvedValue({ id: 77, doctorName: 'BS. Nguyễn Văn A', date: addDays(3), startTime: '09:00:00', endTime: '09:30:00' })
    const wrapper = mountView()
    await flushPromises()
    await pickUntilConfirm(wrapper)

    expect(wrapper.find('[data-test="summary"]').text()).toContain('Khám tổng quát')
    expect(wrapper.find('[data-test="hold-countdown"]').exists()).toBe(true)
    await wrapper.find('#booking-notes').setValue('Ho ba ngày')
    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(appointmentsApi.create).toHaveBeenCalledWith({ slotId: 101, serviceId: 10, notes: 'Ho ba ngày', holdId: 5101 })
    expect(wrapper.find('[data-test="done"]').text()).toContain('#77')
    expect(wrapper.find('[data-test="hold-countdown"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('409: hiện thông báo, tải lại danh sách giờ và giữ nguyên dịch vụ/bác sĩ/ngày', async () => {
    appointmentsApi.create.mockRejectedValue(conflict())
    const wrapper = mountView()
    await flushPromises()
    await pickUntilConfirm(wrapper)
    const loadsBefore = slotsApi.availableByDoctor.mock.calls.length

    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="conflict"]').text()).toContain('vừa được người khác đặt')
    expect(wrapper.find('#booking-step-title').text()).toContain('Bước 4')
    expect(slotsApi.availableByDoctor.mock.calls.length).toBe(loadsBefore + 1)
    expect(slotsApi.availableByDoctor).toHaveBeenLastCalledWith(1, addDays(3), 10)
    // chưa chọn lại giờ nên chưa thể tiếp tục; chọn giờ khác thì đi tiếp được
    expect(next(wrapper).attributes('disabled')).toBeDefined()
    await wrapper.findAll('ul[aria-label="Khung giờ còn trống"] button')[1].trigger('click')
    await flushPromises()
    expect(next(wrapper).attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('410 khi xác nhận: quay về bước chọn giờ với thông điệp của máy chủ, không còn đồng hồ giữ chỗ', async () => {
    appointmentsApi.create.mockRejectedValue(gone())
    const wrapper = mountView()
    await flushPromises()
    await pickUntilConfirm(wrapper)

    await wrapper.findAll('button').find((button) => button.text() === 'Xác nhận đặt lịch').trigger('click')
    await flushPromises()

    expect(wrapper.find('#booking-step-title').text()).toContain('Bước 4')
    expect(wrapper.find('[data-test="conflict"]').text()).toBe('Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ!')
    expect(wrapper.find('[data-test="hold-countdown"]').exists()).toBe(false)
    expect(wrapper.find('ul[aria-label="Khung giờ còn trống"] button[aria-pressed="true"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('Giờ sớm nhất: hiện tối đa 5 dòng cho dịch vụ đã chọn, lọc theo chuyên khoa, chọn một dòng thì tới thẳng bước xác nhận', async () => {
    const wrapper = mountView()
    await flushPromises()
    await pickService(wrapper)

    expect(slotsApi.earliest).toHaveBeenLastCalledWith({ serviceId: 10, specialtyId: undefined, limit: 5 })
    const rows = () => wrapper.findAll('ul[aria-label="Giờ sớm nhất còn trống"] button')
    expect(rows()).toHaveLength(2)
    expect(rows()[0].text()).toContain('08:00 – 08:30')
    expect(rows()[0].text()).toContain('BS. Trần Thị B · Nhi khoa')

    await wrapper.find('#booking-earliest-specialty').setValue('6')
    await flushPromises()
    expect(slotsApi.earliest).toHaveBeenLastCalledWith({ serviceId: 10, specialtyId: '6', limit: 5 })

    await rows()[0].trigger('click')
    await flushPromises()

    expect(slotsApi.hold).toHaveBeenCalledWith(301)
    expect(wrapper.find('#booking-step-title').text()).toContain('Bước 5')
    const summary = wrapper.find('[data-test="summary"]').text()
    expect(summary).toContain('BS. Trần Thị B')
    expect(summary).toContain('Nhi khoa')
    expect(summary).toContain('08:00 – 08:30')
    expect(summary).toContain('Khám tổng quát')
    expect(wrapper.find('[data-test="hold-countdown"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('giờ sớm nhất: không có gợi ý thì báo rõ; lỗi tải có nút thử lại và không chặn việc đặt lịch', async () => {
    slotsApi.earliest.mockResolvedValueOnce([])
    const wrapper = mountView()
    await flushPromises()
    await pickService(wrapper)
    expect(wrapper.find('[data-test="earliest"]').text()).toContain('Chưa có khung giờ trống phù hợp trong 30 ngày tới')

    slotsApi.earliest.mockRejectedValueOnce(new ApiRequestError({ status: 500, code: 'INTERNAL_ERROR' }))
    await wrapper.find('#booking-service').setValue('11')
    await flushPromises()
    const block = wrapper.find('[data-test="earliest"]')
    expect(block.find('[role="alert"]').text()).toContain('Hệ thống đang bận')
    expect(next(wrapper).attributes('disabled')).toBeUndefined()

    await block.findAll('button').find((button) => button.text() === 'Thử lại').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('ul[aria-label="Giờ sớm nhất còn trống"] button')).toHaveLength(2)
    wrapper.unmount()
  })

  it('nút xác nhận khóa khi đang gửi: bấm hai lần chỉ gửi một yêu cầu', async () => {
    let resolveCreate
    appointmentsApi.create.mockReturnValue(new Promise((resolve) => (resolveCreate = resolve)))
    const wrapper = mountView()
    await flushPromises()
    await pickUntilConfirm(wrapper)

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
