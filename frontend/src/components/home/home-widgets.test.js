import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import QuickBooking from './QuickBooking.vue'
import ClinicStats from './ClinicStats.vue'
import ReviewCarousel from './ReviewCarousel.vue'
import { earliestSlots, upcomingDays } from '@/components/booking/bookingLink'
import { doctorsApi } from '@/api/doctors'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/doctors', () => ({ doctorsApi: { list: vi.fn(), slots: vi.fn() } }))

const RouterLink = { name: 'RouterLink', props: ['to'], template: '<a :href="to"><slot /></a>' }
const global = { stubs: { RouterLink } }
const slot = (id, start) => ({ id, startTime: start, endTime: start.replace(':00:', ':30:') })
const review = (id, rating = 5) => ({ id, rating, comment: `Nhận xét ${id}`, reviewerName: `Người ${id}`, doctorId: 1, doctorName: 'Bác sĩ Lan', visitDate: '2026-11-01' })

beforeEach(() => {
  setActivePinia(createPinia())
  doctorsApi.list.mockReset()
  doctorsApi.slots.mockReset()
})

describe('earliestSlots', () => {
  it('ghép giờ trống của nhiều bác sĩ, sắp theo giờ và lấy các giờ sớm nhất', () => {
    const result = earliestSlots([
      { doctor: { id: 1 }, slots: [slot(11, '09:00:00'), slot(12, '14:00:00')] },
      { doctor: { id: 2 }, slots: [slot(21, '08:00:00'), slot(22, '09:00:00')] },
      { doctor: { id: 3 }, slots: null }
    ], 3)
    expect(result.map((r) => r.slot.id)).toEqual([21, 11, 22])
  })
})

describe('Đặt lịch nhanh', () => {
  const specialties = [{ id: 3, name: 'Tim mạch' }]

  it('tìm theo chuyên khoa và ngày, hiện giờ sớm nhất dẫn thẳng tới trang đặt lịch', async () => {
    doctorsApi.list.mockResolvedValue({ content: [{ id: 7, fullName: 'Bác sĩ Lan', specialtyName: 'Tim mạch' }, { id: 8, fullName: 'Bác sĩ Huy' }] })
    doctorsApi.slots.mockImplementation((id) => Promise.resolve(id === 7 ? [slot(70, '10:00:00')] : [slot(80, '08:00:00')]))
    const wrapper = mount(QuickBooking, { props: { specialties }, global })
    const tomorrow = upcomingDays(2)[1].value
    await wrapper.find('#quick-specialty').setValue('3')
    await wrapper.find('#quick-date').setValue(tomorrow)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(doctorsApi.list).toHaveBeenCalledWith({ size: 10, specialtyId: 3 })
    expect(doctorsApi.slots).toHaveBeenCalledWith(7, tomorrow)
    const links = wrapper.findAll('[data-test="quick-slot"]')
    expect(links.map((l) => l.text())).toEqual([expect.stringContaining('08:00'), expect.stringContaining('10:00')])
    expect(links[0].attributes('href')).toBe(`/patient/booking?doctorId=8&slotId=80&date=${tomorrow}&start=08:00&end=08:30`)
    expect(wrapper.find('[data-test="quick-results"]').attributes('aria-live')).toBe('polite')
  })

  it('không còn giờ trống thì gợi ý chọn ngày khác', async () => {
    doctorsApi.list.mockResolvedValue({ content: [{ id: 7, fullName: 'Bác sĩ Lan' }] })
    doctorsApi.slots.mockResolvedValue([])
    const wrapper = mount(QuickBooking, { props: { specialties }, global })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(doctorsApi.list).toHaveBeenCalledWith({ size: 10 })
    expect(wrapper.text()).toContain('Không còn giờ trống hôm nay')
    expect(wrapper.find('a[href="/doctors"]').exists()).toBe(true)
  })

  it('bác sĩ đăng nhập thấy giờ trống nhưng không có liên kết đặt lịch của bệnh nhân', async () => {
    useAuthStore().$patch({ token: 't', user: { id: 1, roles: ['DOCTOR'] } })
    doctorsApi.list.mockResolvedValue({ content: [{ id: 7, fullName: 'Bác sĩ Lan' }] })
    doctorsApi.slots.mockResolvedValue([slot(70, '10:00:00')])
    const wrapper = mount(QuickBooking, { props: { specialties }, global })
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-test="quick-slot"]').element.tagName).toBe('DIV')
    expect(wrapper.text()).toContain('Chỉ tài khoản bệnh nhân đặt được lịch trực tuyến.')
  })
})

describe('Dải số liệu', () => {
  it('lấy số liệu thật từ /clinic, điểm viết theo kiểu Việt Nam', () => {
    const wrapper = mount(ClinicStats, {
      props: { clinic: { doctorCount: 2, specialtyCount: 5, serviceCount: 4, averageRating: 4.66, reviewCount: 3 } },
      global
    })
    expect(wrapper.find('[data-test="stat-doctors"]').text()).toBe('2')
    expect(wrapper.find('[data-test="stat-rating"]').text()).toBe('4,7/5')
    expect(wrapper.text()).toContain('điểm từ 3 đánh giá')
    expect(wrapper.findAll('a').map((a) => a.attributes('href'))).toEqual(['/doctors', '/specialties', '/services', '/reviews'])
  })

  it('không có dữ liệu thì không hiện', () => {
    expect(mount(ClinicStats, { global }).find('ul').exists()).toBe(false)
  })
})

describe('Băng chuyền đánh giá', () => {
  let matchMedia
  beforeEach(() => {
    vi.useFakeTimers()
    matchMedia = window.matchMedia
    window.matchMedia = vi.fn(() => ({ matches: false }))
  })
  afterEach(() => {
    vi.useRealTimers()
    window.matchMedia = matchMedia
  })

  const firstSlide = (wrapper) => wrapper.find('[data-test="carousel-slide"]').text()

  it('tự chuyển, có nút tạm dừng và nút trước/sau', async () => {
    const wrapper = mount(ReviewCarousel, { props: { reviews: [1, 2, 3, 4].map((id) => review(id)), intervalMs: 1000 }, global, attachTo: document.body })
    expect(firstSlide(wrapper)).toContain('Nhận xét 1')
    expect(wrapper.find('[data-test="carousel-slides"]').attributes('aria-live')).toBe('off')
    await vi.advanceTimersByTimeAsync(1000)
    expect(firstSlide(wrapper)).toContain('Nhận xét 2')

    const toggle = wrapper.find('[data-test="carousel-toggle"]')
    expect(toggle.attributes('aria-label')).toBe('Tạm dừng tự chuyển đánh giá')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-label')).toBe('Bật tự chuyển đánh giá')
    expect(wrapper.find('[data-test="carousel-slides"]').attributes('aria-live')).toBe('polite')
    await vi.advanceTimersByTimeAsync(3000)
    expect(firstSlide(wrapper)).toContain('Nhận xét 2')

    await wrapper.find('[data-test="carousel-next"]').trigger('click')
    expect(firstSlide(wrapper)).toContain('Nhận xét 3')
    await wrapper.find('[data-test="carousel-prev"]').trigger('click')
    await wrapper.find('[data-test="carousel-prev"]').trigger('click')
    await wrapper.find('[data-test="carousel-prev"]').trigger('click')
    expect(firstSlide(wrapper)).toContain('Nhận xét 4')
    wrapper.unmount()
  })

  it('dừng khi rê chuột hoặc khi focus nằm trong băng chuyền', async () => {
    const wrapper = mount(ReviewCarousel, { props: { reviews: [1, 2, 3, 4].map((id) => review(id)), intervalMs: 1000 }, global, attachTo: document.body })
    await wrapper.find('section').trigger('mouseenter')
    await vi.advanceTimersByTimeAsync(2000)
    expect(firstSlide(wrapper)).toContain('Nhận xét 1')
    await wrapper.find('section').trigger('mouseleave')

    await wrapper.find('[data-test="carousel-next"]').trigger('focusin')
    await vi.advanceTimersByTimeAsync(2000)
    expect(firstSlide(wrapper)).toContain('Nhận xét 1')
    wrapper.unmount()
  })

  it('người dùng bật "giảm chuyển động" thì mặc định không tự chuyển', async () => {
    window.matchMedia = vi.fn(() => ({ matches: true }))
    const wrapper = mount(ReviewCarousel, { props: { reviews: [1, 2, 3, 4].map((id) => review(id)), intervalMs: 1000 }, global })
    await vi.advanceTimersByTimeAsync(3000)
    expect(firstSlide(wrapper)).toContain('Nhận xét 1')
    expect(wrapper.find('[data-test="carousel-toggle"]').attributes('aria-label')).toBe('Bật tự chuyển đánh giá')
  })

  it('từ 3 đánh giá trở xuống thì hiện hết, không tự chuyển và không có nút dừng', async () => {
    const wrapper = mount(ReviewCarousel, { props: { reviews: [review(1), review(2)], intervalMs: 1000 }, global })
    expect(wrapper.findAll('[data-test="carousel-slide"]')).toHaveLength(2)
    expect(wrapper.find('[data-test="carousel-toggle"]').exists()).toBe(false)
    await vi.advanceTimersByTimeAsync(3000)
    expect(firstSlide(wrapper)).toContain('Nhận xét 1')
  })

  it('không có đánh giá thì không hiện khối', () => {
    expect(mount(ReviewCarousel, { global }).find('section').exists()).toBe(false)
  })
})
