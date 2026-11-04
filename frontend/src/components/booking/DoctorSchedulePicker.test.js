import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DoctorSchedulePicker from './DoctorSchedulePicker.vue'
import DoctorCard from './DoctorCard.vue'
import { doctorBadges } from './doctorBadges'
import { doctorsApi } from '@/api/doctors'

vi.mock('@/api/doctors', () => ({ doctorsApi: { slots: vi.fn() } }))
vi.mock('./clinicFacts', () => ({ loadClinicFacts: vi.fn(() => Promise.resolve(null)) }))

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const iso = (offset) => {
  const now = new Date()
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const slot = (id, start, end) => ({ id, startTime: start, endTime: end })

function mountPicker(props = {}) {
  return mount(DoctorSchedulePicker, {
    props: { doctorId: 1, showFacts: false, ...props },
    global: { stubs: { RouterLink } },
    attachTo: document.body
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  doctorsApi.slots.mockReset()
})

describe('Dải 7 ngày của lịch khám', () => {
  it('là nhóm radio 7 nút, chỉ ngày đang chọn nhận Tab', async () => {
    doctorsApi.slots.mockResolvedValue([slot(1, '08:00:00', '08:30:00')])
    const wrapper = mountPicker()
    await flushPromises()
    expect(wrapper.find('[role="radiogroup"]').attributes('aria-label')).toBe('Chọn ngày khám')
    const days = wrapper.findAll('[role="radio"]')
    expect(days).toHaveLength(7)
    expect(days[0].text()).toContain('Hôm nay')
    expect(days[0].attributes('aria-checked')).toBe('true')
    expect(days.map((d) => d.attributes('tabindex'))).toEqual(['0', '-1', '-1', '-1', '-1', '-1', '-1'])
    wrapper.unmount()
  })

  it('tự mở ở ngày gần nhất còn lịch và đánh dấu "hết lịch" cho ngày đã tải mà trống', async () => {
    doctorsApi.slots.mockImplementation((_, date) => Promise.resolve(date === iso(2) ? [slot(5, '09:00:00', '09:30:00')] : []))
    const wrapper = mountPicker()
    await flushPromises()
    const days = wrapper.findAll('[role="radio"]')
    expect(days[2].attributes('aria-checked')).toBe('true')
    expect(days[0].text()).toContain('hết lịch')
    expect(days[1].text()).toContain('hết lịch')
    // ngày chưa tải không bị đánh dấu
    expect(days[3].attributes('data-status')).toBe('unknown')
    expect(days[3].text()).not.toContain('hết lịch')
    wrapper.unmount()
  })

  it('ngày đang chọn hết lịch: trạng thái rỗng có hình và nút tới ngày còn lịch đã biết', async () => {
    doctorsApi.slots.mockImplementation((_, date) => Promise.resolve(date === iso(2) ? [slot(5, '09:00:00', '09:30:00')] : []))
    const wrapper = mountPicker()
    await flushPromises()
    await wrapper.findAll('[role="radio"]')[0].trigger('click')
    await flushPromises()
    const empty = wrapper.find('[data-test="no-slots"]')
    expect(empty.find('svg[data-illustration="calendar"]').exists()).toBe(true)
    const jump = wrapper.find('[data-test="jump-next-available"]')
    const [, month, day] = iso(2).split('-').map(Number)
    expect(jump.text()).toMatch(new RegExp(`^Xem lịch .+ - ${day}/${month}$`))
    await jump.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[role="radio"]')[2].attributes('aria-checked')).toBe('true')
    wrapper.unmount()
  })

  it('phím mũi tên, Home, End chuyển ngày, chuyển focus và tải khung giờ ngày mới', async () => {
    doctorsApi.slots.mockResolvedValue([slot(1, '08:00:00', '08:30:00')])
    const wrapper = mountPicker()
    await flushPromises()
    const group = wrapper.find('[role="radiogroup"]')
    await group.trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    let days = wrapper.findAll('[role="radio"]')
    expect(days[1].attributes('aria-checked')).toBe('true')
    expect(document.activeElement).toBe(days[1].element)
    expect(doctorsApi.slots).toHaveBeenLastCalledWith(1, iso(1))

    await group.trigger('keydown', { key: 'End' })
    await flushPromises()
    days = wrapper.findAll('[role="radio"]')
    expect(days[6].attributes('aria-checked')).toBe('true')

    await group.trigger('keydown', { key: 'ArrowRight' }) // vòng lại đầu dải
    await flushPromises()
    expect(wrapper.findAll('[role="radio"]')[0].attributes('aria-checked')).toBe('true')

    await group.trigger('keydown', { key: 'ArrowLeft' })
    await flushPromises()
    expect(wrapper.findAll('[role="radio"]')[6].attributes('aria-checked')).toBe('true')
    wrapper.unmount()
  })

  it('lần đổi ngày đầu tiên vẫn tải khung giờ khi hôm nay còn lịch', async () => {
    doctorsApi.slots.mockResolvedValue([slot(1, '08:00:00', '08:30:00')])
    const wrapper = mountPicker()
    await flushPromises()
    doctorsApi.slots.mockClear()
    await wrapper.findAll('[role="radio"]')[3].trigger('click')
    await flushPromises()
    expect(doctorsApi.slots).toHaveBeenCalledWith(1, iso(3))
    wrapper.unmount()
  })

  it('khung giờ đầu tiên có nhãn "Sớm nhất", các khung sau thì không', async () => {
    doctorsApi.slots.mockResolvedValue([slot(1, '08:00:00', '08:30:00'), slot(2, '08:30:00', '09:00:00')])
    const wrapper = mountPicker()
    await flushPromises()
    const links = wrapper.findAll('[data-test="slot-link"]')
    expect(links[0].find('[data-test="earliest-tag"]').exists()).toBe(true)
    expect(links[0].attributes('aria-label')).toContain('khung giờ sớm nhất')
    expect(links[1].find('[data-test="earliest-tag"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('báo khung giờ sớm nhất của ngày đang xem cho thanh đặt lịch trên điện thoại', async () => {
    doctorsApi.slots.mockImplementation((_, date) => Promise.resolve(date === iso(0) ? [slot(3, '08:00:00', '08:30:00')] : []))
    const wrapper = mountPicker()
    await flushPromises()
    const [first] = wrapper.emitted('earliest').at(-1)
    expect(first.slot.id).toBe(3)
    expect(first.label).toContain('Hôm nay')
    expect(first.link).toBe(`/patient/booking?doctorId=1&slotId=3&date=${iso(0)}&start=08:00&end=08:30`)

    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    await flushPromises()
    expect(wrapper.emitted('earliest').at(-1)).toEqual([null])
    wrapper.unmount()
  })

  it('đang tải thì hiện khung xương và thông báo cho trình đọc màn hình', async () => {
    doctorsApi.slots.mockReturnValue(new Promise(() => {}))
    const wrapper = mountPicker()
    await flushPromises()
    expect(wrapper.find('[data-test="slots-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[role="status"]').text()).toBe('Đang tải khung giờ…')
    wrapper.unmount()
  })
})

describe('Huy hiệu bác sĩ', () => {
  it('tính từ kinh nghiệm, điểm và số đánh giá', () => {
    const keys = doctorBadges({ yearsOfExperience: 12, averageRating: 4.8, reviewCount: 3 }).map((b) => b.key)
    expect(keys).toEqual(['experience', 'top', 'many'])
    expect(doctorBadges({ averageRating: 4.8, reviewCount: 3 })[0].label).toBe('Đánh giá 4,8★')
  })

  it('không gắn huy hiệu khi thiếu dữ liệu hoặc chưa đạt ngưỡng', () => {
    expect(doctorBadges({ yearsOfExperience: 0, averageRating: 4.4, reviewCount: 2 })).toEqual([])
    expect(doctorBadges({ averageRating: 5, reviewCount: 0 })).toEqual([])
    expect(doctorBadges(null)).toEqual([])
  })

  it('"Còn lịch hôm nay" đứng đầu khi hôm nay còn khung giờ', () => {
    expect(doctorBadges({}, { hasSlotsToday: true }).map((b) => b.key)).toEqual(['today'])
  })

  it('thẻ bác sĩ hiện "Còn lịch hôm nay" sau khi khối lịch báo hôm nay còn chỗ', async () => {
    doctorsApi.slots.mockResolvedValue([slot(1, '08:00:00', '08:30:00')])
    const wrapper = mount(DoctorCard, {
      props: { doctor: { id: 1, fullName: 'Bác sĩ Lan', specialtyName: 'Nội tổng quát', yearsOfExperience: 12, averageRating: 5, reviewCount: 1 } },
      global: { stubs: { RouterLink } }
    })
    await flushPromises()
    expect(wrapper.find('[data-test="badge-today"]').text()).toBe('Còn lịch hôm nay')
    expect(wrapper.find('[data-test="badge-experience"]').text()).toBe('12 năm kinh nghiệm')
    expect(wrapper.find('[data-test="badge-many"]').exists()).toBe(false)
  })

  it('không hiện "Còn lịch hôm nay" khi hôm nay hết lịch', async () => {
    doctorsApi.slots.mockImplementation((_, date) => Promise.resolve(date === iso(1) ? [slot(1, '08:00:00', '08:30:00')] : []))
    const wrapper = mount(DoctorCard, {
      props: { doctor: { id: 1, fullName: 'Bác sĩ Lan' } },
      global: { stubs: { RouterLink } }
    })
    await flushPromises()
    expect(wrapper.find('[data-test="badge-today"]').exists()).toBe(false)
  })
})
