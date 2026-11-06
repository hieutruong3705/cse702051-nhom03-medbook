import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { buildIcs, escapeText, foldLine } from './ics'
import { countUp, reveal } from './motion'
import EmptyState from '@/components/ui/EmptyState.vue'
import BookingSuccess from '@/views/patient/booking/BookingSuccess.vue'

vi.mock('@/components/booking/clinicFacts', () => ({
  loadClinicFacts: vi.fn(() => Promise.resolve({ name: 'Phòng khám đa khoa MedBook', address: 'Số 1 Đường Mô Phỏng, Hà Nội', minPrice: 150000 }))
}))

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const unfold = (text) => text.replace(/\r\n /g, '')

describe('Tệp lịch .ics', () => {
  const ics = buildIcs({
    id: 77,
    title: 'Khám tổng quát với Bác sĩ Lan (MedBook)',
    description: 'Mã lịch hẹn #77; thanh toán tại quầy, sau khi khám',
    location: 'Phòng khám đa khoa MedBook, Số 1 Đường Mô Phỏng, Hà Nội',
    date: '2026-11-06',
    startTime: '09:00:00',
    endTime: '09:30',
    now: new Date('2026-11-05T02:00:00Z')
  })

  it('giờ khám là giờ Việt Nam (TZID, +07:00), không đổi sang UTC', () => {
    expect(ics).toContain('DTSTART;TZID=Asia/Ho_Chi_Minh:20261106T090000')
    expect(ics).toContain('DTEND;TZID=Asia/Ho_Chi_Minh:20261106T093000')
    expect(ics).toContain('TZOFFSETTO:+0700')
    expect(ics).toContain('DTSTAMP:20261105T020000Z')
  })

  it('có tiêu đề, địa chỉ, mã định danh và nhắc trước 1 giờ', () => {
    const text = unfold(ics)
    expect(text).toContain('SUMMARY:Khám tổng quát với Bác sĩ Lan (MedBook)')
    expect(text).toContain('LOCATION:Phòng khám đa khoa MedBook\\, Số 1 Đường Mô Phỏng\\, Hà Nội')
    expect(text).toContain('DESCRIPTION:Mã lịch hẹn #77\\; thanh toán tại quầy\\, sau khi khám')
    expect(text).toContain('UID:appointment-77@medbook.local')
    expect(text).toContain('TRIGGER:-PT1H')
  })

  it('đúng khung VCALENDAR/VEVENT, xuống dòng CRLF, dòng không quá 75 octet', () => {
    expect(ics.startsWith('BEGIN:VCALENDAR\r\nVERSION:2.0\r\n')).toBe(true)
    expect(ics.endsWith('END:VEVENT\r\nEND:VCALENDAR\r\n')).toBe(true)
    const encoder = new TextEncoder()
    for (const line of ics.split('\r\n')) expect(encoder.encode(line).length).toBeLessThanOrEqual(75)
  })

  it('thoát ký tự đặc biệt và gập dòng không cắt giữa ký tự tiếng Việt', () => {
    expect(escapeText('a\\b;c,d\ne')).toBe('a\\\\b\\;c\\,d\\ne')
    const long = 'SUMMARY:' + 'Đặt lịch khám '.repeat(10)
    const folded = foldLine(long)
    expect(folded).toContain('\r\n ')
    expect(unfold(folded)).toBe(long)
  })
})

describe('Màn hình đặt lịch thành công', () => {
  const appointment = { id: 77, doctorName: 'Bác sĩ Lan', date: '2026-11-06', startTime: '09:00:00', endTime: '09:30:00' }

  it('có dấu tích, tóm tắt lịch hẹn và địa chỉ phòng khám', async () => {
    const wrapper = mount(BookingSuccess, { props: { appointment, service: { name: 'Khám tổng quát' } }, global: { stubs: { RouterLink } } })
    await flushPromises()
    expect(wrapper.find('[data-test="success-check"]').attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('[role="status"]').text()).toContain('#77')
    const summary = wrapper.find('[data-test="success-summary"]').text()
    expect(summary).toContain('Bác sĩ Lan')
    expect(summary).toContain('Khám tổng quát')
    expect(summary).toContain('09:00–09:30')
    expect(summary).toContain('Số 1 Đường Mô Phỏng')
  })

  it('nút "Thêm vào lịch" tải tệp .ics có đúng giờ và địa chỉ', async () => {
    const created = []
    URL.createObjectURL = vi.fn((blob) => {
      created.push(blob)
      return 'blob:medbook/1'
    })
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = mount(BookingSuccess, { props: { appointment, service: { name: 'Khám tổng quát' } }, global: { stubs: { RouterLink } } })
    await flushPromises()

    await wrapper.find('[data-test="add-to-calendar"]').trigger('click')
    expect(click).toHaveBeenCalledTimes(1)
    expect(created[0].type).toBe('text/calendar;charset=utf-8')
    const text = unfold(wrapper.vm.icsContent())
    expect(text).toContain('DTSTART;TZID=Asia/Ho_Chi_Minh:20261106T090000')
    expect(text).toContain('SUMMARY:Khám tổng quát với Bác sĩ Lan (MedBook)')
    expect(text).toContain('LOCATION:Phòng khám đa khoa MedBook\\, Số 1 Đường Mô Phỏng\\, Hà Nội')
    click.mockRestore()
  })
})

describe('Trạng thái rỗng có hình minh họa', () => {
  it('hình là trang trí, tiêu đề đổi được cấp', () => {
    const wrapper = mount(EmptyState, { props: { title: 'Chưa có đánh giá', illustration: 'reviews', headingTag: 'h3', message: 'Gợi ý' } })
    expect(wrapper.find('svg').attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('svg').attributes('data-illustration')).toBe('reviews')
    expect(wrapper.find('h3').text()).toBe('Chưa có đánh giá')
    expect(wrapper.find('i').exists()).toBe(false)
  })

  it('không chọn hình thì giữ biểu tượng như trước', () => {
    const wrapper = mount(EmptyState, { props: { title: 'Trống' } })
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.find('h2').text()).toBe('Trống')
  })
})

describe('Chuyển động', () => {
  let matchMedia
  beforeEach(() => {
    matchMedia = window.matchMedia
  })
  afterEach(() => {
    window.matchMedia = matchMedia
    delete window.IntersectionObserver
  })

  it('đếm số: giảm chuyển động thì hiện thẳng giá trị cuối', () => {
    window.matchMedia = vi.fn(() => ({ matches: true }))
    const frames = []
    countUp(12, (v) => frames.push(v))
    expect(frames).toEqual([12])
  })

  it('v-reveal: phần tử ngoài màn hình được ẩn rồi hiện khi cuộn tới, chỉ một lần', () => {
    window.matchMedia = vi.fn(() => ({ matches: false }))
    let callback
    const observe = vi.fn()
    const disconnect = vi.fn()
    window.IntersectionObserver = vi.fn(function (cb) {
      callback = cb
      this.observe = observe
      this.disconnect = disconnect
    })
    const el = document.createElement('div')
    el.getBoundingClientRect = () => ({ top: 5000, bottom: 5100 })
    reveal.mounted(el, { value: 2 })
    expect(el.classList.contains('reveal')).toBe(true)
    expect(el.style.transitionDelay).toBe('140ms')
    callback([{ isIntersecting: true, target: el }])
    expect(el.classList.contains('reveal-in')).toBe(true)
    expect(disconnect).toHaveBeenCalled()
  })

  it('v-reveal: giảm chuyển động hoặc phần tử đã trong màn hình thì không ẩn', () => {
    window.IntersectionObserver = vi.fn()
    window.matchMedia = vi.fn(() => ({ matches: true }))
    const a = document.createElement('div')
    a.getBoundingClientRect = () => ({ top: 5000, bottom: 5100 })
    reveal.mounted(a, {})
    expect(a.classList.contains('reveal')).toBe(false)

    window.matchMedia = vi.fn(() => ({ matches: false }))
    const b = document.createElement('div')
    b.getBoundingClientRect = () => ({ top: 10, bottom: 100 })
    reveal.mounted(b, {})
    expect(b.classList.contains('reveal')).toBe(false)
  })
})
