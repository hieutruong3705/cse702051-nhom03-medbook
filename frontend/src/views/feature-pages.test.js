import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ChangePasswordView from './account/ChangePasswordView.vue'
import ProfileView from './account/ProfileView.vue'
import ForgotPasswordView from './auth/ForgotPasswordView.vue'
import LoginView from './auth/LoginView.vue'
import RegisterView from './auth/RegisterView.vue'
import ResetPasswordView from './auth/ResetPasswordView.vue'
import DoctorsView from './public/DoctorsView.vue'
import ServicesView from './public/ServicesView.vue'
import SpecialtiesView from './public/SpecialtiesView.vue'

const push = vi.fn()
const replace = vi.fn()

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push, replace })
  }
})

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn().mockResolvedValue({ token: 'token', roles: ['PATIENT'] }),
    register: vi.fn().mockResolvedValue({}),
    forgotPassword: vi.fn().mockResolvedValue({}),
    resetPassword: vi.fn().mockResolvedValue({}),
    changePassword: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('@/api/doctors', () => ({
  doctorsApi: {
    list: vi.fn().mockResolvedValue({
      content: [{ id: 1, fullName: 'Bác sĩ Lan', specialtyId: 1, specialtyName: 'Nội tổng quát', bio: 'Mười năm kinh nghiệm' }],
      page: 0, size: 20, totalElements: 1, totalPages: 1
    })
  }
}))

vi.mock('@/api/catalog', () => ({
  catalogApi: {
    specialties: {
      list: vi.fn().mockResolvedValue({ content: [{ id: 1, name: 'Noi tong quat' }] })
    },
    services: {
      list: vi.fn().mockResolvedValue({ content: [{ id: 1, name: 'Kham tong quat', durationMinutes: 30, price: 150000 }] })
    }
  }
}))

vi.mock('@/api/users', () => ({
  usersApi: {
    me: vi.fn().mockResolvedValue({ fullName: 'Nguyen Minh An', phone: '0900000000', email: 'an@example.com' }),
    updateMe: vi.fn().mockResolvedValue({})
  }
}))

describe('feature page smoke tests', () => {
  it.each([
    [LoginView, 'Đăng nhập'],
    [RegisterView, 'Tạo tài khoản bệnh nhân'],
    [ForgotPasswordView, 'Quên mật khẩu'],
    [ResetPasswordView, 'Đặt lại mật khẩu'],
    [ChangePasswordView, 'Đổi mật khẩu']
  ])('renders auth page %s', (component, text) => {
    const wrapper = mount(component)
    expect(wrapper.text()).toContain(text)
  })

  it('renders public doctors page with mocked rows', async () => {
    const wrapper = mount(DoctorsView)
    await flushPromises()
    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(wrapper.text()).toContain('Nội tổng quát')
    // hồ sơ công khai không có số giấy phép; nút đặt lịch mang sẵn mã bác sĩ
    expect(wrapper.text()).not.toContain('Mã hành nghề')
    expect(wrapper.find('a[href="/patient/booking?doctorId=1"]').text()).toContain('Đặt lịch')
    expect(wrapper.find('#doctor-specialty').text()).toContain('Noi tong quat')
  })

  it('renders public specialties page with mocked cards', async () => {
    const wrapper = mount(SpecialtiesView)
    await flushPromises()
    expect(wrapper.text()).toContain('Noi tong quat')
  })

  it('renders public services page with mocked rows', async () => {
    const wrapper = mount(ServicesView)
    await flushPromises()
    expect(wrapper.text()).toContain('Kham tong quat')
  })

  it('renders account profile page with mocked user data', async () => {
    const wrapper = mount(ProfileView)
    await flushPromises()
    expect(wrapper.text()).toContain('Email')
    expect(wrapper.find('#profile-full-name').element.value).toBe('Nguyen Minh An')
  })
})
