import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MobileBookingBar from './MobileBookingBar.vue'

const RouterLink = { props: ['to'], template: '<a :href="to"><slot /></a>' }
const global = { stubs: { RouterLink, Transition: false } }
const doctor = { id: 1, fullName: 'Bác sĩ Lan' }
const earliest = {
  slot: { id: 9, startTime: '08:00:00', endTime: '08:30:00' },
  date: '2026-11-05',
  label: 'Hôm nay - 5/11',
  link: '/patient/booking?doctorId=1&slotId=9&date=2026-11-05&start=08:00&end=08:30'
}

afterEach(() => {
  delete window.IntersectionObserver
  document.body.className = ''
})

describe('Thanh đặt lịch trên điện thoại', () => {
  it('hiện bác sĩ, giờ sớm nhất và nút đặt dẫn thẳng tới khung giờ đó; chỉ hiện dưới 768 px', () => {
    const wrapper = mount(MobileBookingBar, { props: { doctor, earliest }, global })
    const bar = wrapper.find('[data-test="mobile-booking-bar"]')
    expect(bar.classes()).toContain('md:hidden')
    expect(bar.attributes('role')).toBe('region')
    expect(bar.text()).toContain('Bác sĩ Lan')
    expect(wrapper.find('[data-test="mobile-bar-slot"]').text()).toBe('Sớm nhất: 08:00 · Hôm nay - 5/11')
    const book = wrapper.find('[data-test="mobile-bar-book"]')
    expect(book.attributes('href')).toBe(earliest.link)
    expect(book.attributes('aria-label')).toBe('Đặt lịch 08:00 Hôm nay - 5/11 với Bác sĩ Lan')
  })

  it('chừa chỗ ở cuối trang khi có thanh và bỏ đi khi rời trang', () => {
    const wrapper = mount(MobileBookingBar, { props: { doctor, earliest }, global })
    expect(document.body.classList.contains('has-mobile-booking-bar')).toBe(true)
    wrapper.unmount()
    expect(document.body.classList.contains('has-mobile-booking-bar')).toBe(false)
  })

  it('ngày đang xem hết lịch hoặc không phải bệnh nhân: nút "Xem lịch" đưa về khối lịch và đặt focus vào ngày đang chọn', async () => {
    const target = document.createElement('section')
    target.innerHTML = '<button role="radio" aria-checked="false">a</button><button role="radio" aria-checked="true">b</button>'
    target.scrollIntoView = vi.fn()
    document.body.appendChild(target)
    const wrapper = mount(MobileBookingBar, { props: { doctor, earliest: null, target }, global })
    expect(wrapper.find('[data-test="mobile-bar-slot"]').text()).toBe('Xem lịch khám 7 ngày tới')
    expect(wrapper.find('[data-test="mobile-bar-book"]').exists()).toBe(false)
    await wrapper.find('[data-test="mobile-bar-schedule"]').trigger('click')
    expect(target.scrollIntoView).toHaveBeenCalled()
    expect(document.activeElement.textContent).toBe('b')

    await wrapper.setProps({ earliest, canBook: false })
    expect(wrapper.find('[data-test="mobile-bar-book"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="mobile-bar-schedule"]').exists()).toBe(true)
    target.remove()
  })

  it('ẩn khi khối lịch khám đang trong màn hình, hiện lại khi cuộn qua', async () => {
    let callback
    window.IntersectionObserver = vi.fn(function (cb) {
      callback = cb
      this.observe = vi.fn()
      this.disconnect = vi.fn()
    })
    const target = document.createElement('section')
    const wrapper = mount(MobileBookingBar, { props: { doctor, earliest, target }, global })
    callback([{ isIntersecting: true }])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="mobile-booking-bar"]').exists()).toBe(false)
    callback([{ isIntersecting: false }])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="mobile-booking-bar"]').exists()).toBe(true)
  })
})
