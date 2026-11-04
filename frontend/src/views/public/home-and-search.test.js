import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from '../Home.vue'
import DoctorsView from './DoctorsView.vue'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
import { clinicApi } from '@/api/clinic'
import { reviewsApi } from '@/api/reviews'
import { useAuthStore } from '@/stores/auth'

const push = vi.fn()
const route = { query: {}, path: '/', fullPath: '/' }

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => ({ push, replace: vi.fn() })
  }
})

vi.mock('@/api/doctors', () => ({
  // slots: thẻ bác sĩ ở trang danh sách tự tải lịch khám
  doctorsApi: { list: vi.fn(), slots: vi.fn(() => Promise.resolve([])) }
}))

vi.mock('@/api/clinic', () => ({
  clinicApi: { get: vi.fn(() => Promise.resolve(null)) }
}))

vi.mock('@/api/reviews', () => ({
  reviewsApi: { listPublic: vi.fn(() => Promise.resolve({ content: [] })) }
}))

vi.mock('@/api/catalog', () => ({
  catalogApi: { specialties: { list: vi.fn() }, services: { list: vi.fn(() => Promise.resolve({ content: [] })) } }
}))

const doctorPage = {
  content: [{ id: 7, fullName: 'Trần Thị Lan', specialtyId: 3, specialtyName: 'Tim mạch', bio: '' }],
  page: 0, size: 20, totalElements: 1, totalPages: 1
}
const specialtyPage = { content: [{ id: 3, name: 'Tim mạch' }] }

function signIn(roles) {
  const auth = useAuthStore()
  auth.$patch({ token: 'token', user: { id: 1, username: 'demo', fullName: 'Người dùng thử', roles } })
}

beforeEach(() => {
  push.mockClear()
  route.query = {}
  doctorsApi.list.mockReset().mockResolvedValue(doctorPage)
  catalogApi.specialties.list.mockReset().mockResolvedValue(specialtyPage)
})

describe('Trang chủ', () => {
  it('hiện bác sĩ kèm tên chuyên khoa lấy từ API và chỉ tải một trang ngắn', async () => {
    const wrapper = mount(Home)
    await flushPromises()

    expect(doctorsApi.list).toHaveBeenCalledWith({ size: 8 })
    expect(catalogApi.specialties.list).toHaveBeenCalledWith({ size: 8 })
    // Thẻ bác sĩ và chuyên khoa dẫn tới trang chi tiết công khai (xem được khi chưa đăng nhập)
    const card = wrapper.find('a[href="/doctors/7"]')
    expect(card.text()).toContain('Trần Thị Lan')
    expect(card.text()).toContain('Tim mạch')
    expect(card.text()).toContain('0 đánh giá')
    expect(wrapper.find('a[href="/specialties/3"]').text()).toContain('Tim mạch')
  })

  it('có khối đặt lịch nhanh, dải số liệu từ /clinic và băng chuyền đánh giá', async () => {
    clinicApi.get.mockResolvedValueOnce({ doctorCount: 2, specialtyCount: 5, serviceCount: 4, averageRating: 4.7, reviewCount: 3 })
    reviewsApi.listPublic.mockResolvedValueOnce({ content: [{ id: 1, rating: 5, comment: 'Rất tốt', reviewerName: 'Nguyễn V. A.', doctorId: 7, doctorName: 'Trần Thị Lan' }] })
    const wrapper = mount(Home)
    await flushPromises()

    expect(reviewsApi.listPublic).toHaveBeenCalledWith({ size: 6, sort: 'rating,desc' })
    expect(wrapper.find('[data-test="quick-booking"]').exists()).toBe(true)
    expect(wrapper.find('#quick-specialty').text()).toContain('Tim mạch')
    expect(wrapper.find('[data-test="stat-specialties"]').text()).toBe('5')
    expect(wrapper.find('[data-test="review-carousel"]').text()).toContain('Rất tốt')
  })

  it('ô tìm kiếm có nhãn và chuyển sang danh sách bác sĩ kèm từ khóa', async () => {
    const wrapper = mount(Home)
    await flushPromises()

    expect(wrapper.find('label[for="home-search"]').text()).toBe('Tìm bác sĩ hoặc chuyên khoa')
    await wrapper.find('#home-search').setValue('  tim mạch ')
    await wrapper.find('form[role="search"]').trigger('submit')
    expect(push).toHaveBeenCalledWith({ path: '/doctors', query: { keyword: 'tim mạch' } })

    await wrapper.find('#home-search').setValue('   ')
    await wrapper.find('form[role="search"]').trigger('submit')
    expect(push).toHaveBeenLastCalledWith({ path: '/doctors', query: {} })
  })

  it('khách và bệnh nhân được dẫn tới trang đặt lịch', async () => {
    const wrapper = mount(Home)
    await flushPromises()
    const bookButton = wrapper.findAll('button').find((button) => button.text().includes('Đặt khám'))
    await bookButton.trigger('click')
    expect(push).toHaveBeenLastCalledWith('/patient/booking')
  })

  it('bác sĩ và quản trị viên không bị dẫn tới trang đặt lịch của bệnh nhân', async () => {
    signIn(['DOCTOR'])
    const wrapper = mount(Home)
    await flushPromises()

    expect(wrapper.find('a[href="/patient/booking?doctorId=7"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/doctors/7"]').exists()).toBe(true)
    const bookButton = wrapper.findAll('button').find((button) => button.text().includes('Đặt khám'))
    await bookButton.trigger('click')
    expect(push).toHaveBeenLastCalledWith('/doctors')
  })

  it('chân trang ghi tên đề tài, nhóm, lớp học phần và dòng dữ liệu mô phỏng', async () => {
    const wrapper = mount(Home)
    await flushPromises()
    const info = wrapper.find('footer [data-test="project-info"]').text()
    expect(info).toContain('Đề tài: Hệ thống quản lý bệnh án và đặt lịch khám bệnh')
    expect(info).toContain('Nhóm 03')
    expect(info).toContain('Lớp học phần Thiết kế web nâng cao (CSE702051)')
    expect(wrapper.find('footer [data-test="data-notice"]').text()).toBe('Dữ liệu mô phỏng phục vụ mục đích học tập.')
    const dnsExit = wrapper.find('footer [data-test="dnsexit-link"]')
    expect(dnsExit.attributes()).toMatchObject({
      href: 'https://dnsexit.com/',
      target: '_blank',
      rel: 'noopener noreferrer'
    })
  })

  it('chân trang của khách: nút đặt lịch, liên kết khám phá, liên kết tài khoản và dòng bản quyền', async () => {
    const wrapper = mount(Home)
    await flushPromises()
    const footer = wrapper.find('footer')
    expect(footer.findAll('a').map((link) => link.attributes('href'))).toEqual([
      '/', '/patient/booking',
      '/', '/specialties', '/doctors', '/services', '/reviews', '/about',
      '/login', '/register', '/forgot-password',
      'https://dnsexit.com/'
    ])
    expect(footer.find('[data-test="copyright"]').text()).toBe(`© ${new Date().getFullYear()} MedBook`)
  })

  it('chân trang của người đã đăng nhập dẫn về khu làm việc; chỉ khách và bệnh nhân có nút đặt lịch', async () => {
    signIn(['DOCTOR'])
    const doctorFooter = mount(Home).find('footer')
    const doctorLinks = doctorFooter.findAll('a').map((link) => link.attributes('href'))
    expect(doctorLinks).toEqual(expect.arrayContaining(['/doctor/dashboard', '/account/profile', '/account/change-password']))
    expect(doctorLinks).not.toContain('/login')
    expect(doctorFooter.find('[data-test="footer-booking"]').exists()).toBe(false)

    signIn(['PATIENT'])
    const patientFooter = mount(Home).find('footer')
    expect(patientFooter.find('[data-test="footer-booking"]').attributes('href')).toBe('/patient/booking')
    expect(patientFooter.findAll('a').map((link) => link.attributes('href'))).toContain('/patient/dashboard')
  })

  it('API lỗi thì báo danh sách đang được cập nhật thay vì để trang trống', async () => {
    doctorsApi.list.mockRejectedValue(new Error('mất mạng'))
    catalogApi.specialties.list.mockRejectedValue(new Error('mất mạng'))
    const wrapper = mount(Home)
    await flushPromises()

    expect(wrapper.text()).toContain('Danh sách chuyên khoa đang được cập nhật.')
    expect(wrapper.text()).toContain('Danh sách bác sĩ đang được cập nhật.')
  })
})

describe('Danh sách bác sĩ mở từ trang chủ', () => {
  it('lọc sẵn theo từ khóa trên địa chỉ', async () => {
    route.query = { keyword: 'lan' }
    const wrapper = mount(DoctorsView)
    await flushPromises()

    expect(wrapper.find('#doctor-keyword').element.value).toBe('lan')
    expect(doctorsApi.list).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'lan' }))
  })

  it('lọc sẵn theo chuyên khoa trên địa chỉ và bỏ qua giá trị không phải số', async () => {
    route.query = { specialtyId: '3' }
    mount(DoctorsView)
    await flushPromises()
    expect(doctorsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ specialtyId: '3' }))

    route.query = { specialtyId: '3 OR 1=1' }
    mount(DoctorsView)
    await flushPromises()
    expect(doctorsApi.list.mock.calls.at(-1)[0]).not.toHaveProperty('specialtyId')
  })
})
