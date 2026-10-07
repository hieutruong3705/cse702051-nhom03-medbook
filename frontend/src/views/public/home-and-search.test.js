import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from '../Home.vue'
import DoctorsView from './DoctorsView.vue'
import { catalogApi } from '@/api/catalog'
import { doctorsApi } from '@/api/doctors'
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
  doctorsApi: { list: vi.fn() }
}))

vi.mock('@/api/catalog', () => ({
  catalogApi: { specialties: { list: vi.fn() } }
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
    const card = wrapper.find('a[href="/patient/booking?doctorId=7"]')
    expect(card.text()).toContain('Trần Thị Lan')
    expect(card.text()).toContain('Tim mạch')
    expect(wrapper.find('a[href="/doctors?specialtyId=3"]').text()).toContain('Tim mạch')
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
    const bookButton = wrapper.findAll('button').find((button) => button.text().includes('Đặt khám'))
    await bookButton.trigger('click')
    expect(push).toHaveBeenLastCalledWith('/doctors')
  })

  it('chân trang ghi tên đề tài, nhóm, lớp học phần và dòng dữ liệu mô phỏng', async () => {
    const wrapper = mount(Home)
    await flushPromises()
    const footer = wrapper.find('footer').text()
    expect(footer).toContain('Hệ thống quản lý bệnh án và đặt lịch khám bệnh')
    expect(footer).toContain('Nhóm 03')
    expect(footer).toContain('CSE702051')
    expect(wrapper.find('footer [data-test="data-notice"]').text()).toBe('Dữ liệu mô phỏng phục vụ mục đích học tập.')
  })

  it('API lỗi thì báo chưa có dữ liệu thay vì để trang trống', async () => {
    doctorsApi.list.mockRejectedValue(new Error('mất mạng'))
    catalogApi.specialties.list.mockRejectedValue(new Error('mất mạng'))
    const wrapper = mount(Home)
    await flushPromises()

    expect(wrapper.text()).toContain('Chưa có dữ liệu chuyên khoa.')
    expect(wrapper.text()).toContain('Chưa có dữ liệu bác sĩ.')
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
