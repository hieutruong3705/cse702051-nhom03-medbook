import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChangePasswordView from '@/views/account/ChangePasswordView.vue'
import ForgotPasswordView from './ForgotPasswordView.vue'
import LoginView from './LoginView.vue'
import RegisterView from './RegisterView.vue'
import ResetPasswordView from './ResetPasswordView.vue'
import { ApiRequestError } from '@/api/http'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const hoisted = vi.hoisted(() => ({
  route: { query: {} },
  replace: vi.fn(),
  push: vi.fn()
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => hoisted.route,
    useRouter: () => ({ push: hoisted.push, replace: hoisted.replace, resolve: () => ({ matched: [] }) })
  }
})

vi.mock('@/api/auth', () => ({
  authApi: {
    register: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    changePassword: vi.fn(),
    forgotPassword: vi.fn(),
    resetPassword: vi.fn()
  }
}))

const VALID_PASSWORD = 'MedBook@2026'

async function fill(wrapper, values) {
  for (const [id, value] of Object.entries(values)) {
    await wrapper.find(`#${id}`).setValue(value)
  }
}

const submit = async (wrapper) => {
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  hoisted.route.query = {}
})

describe('Đăng ký bệnh nhân', () => {
  const validForm = {
    'register-full-name': 'Nguyễn Văn An',
    'register-phone': '0912345678',
    'register-email': 'an@example.com',
    'register-username': 'nguyenvanan',
    'register-password': VALID_PASSWORD,
    'register-confirm-password': VALID_PASSWORD
  }

  it('không gọi API khi dữ liệu sai và báo lỗi đúng trường bằng tiếng Việt', async () => {
    const wrapper = mount(RegisterView)
    await fill(wrapper, { ...validForm, 'register-phone': '', 'register-password': 'abc123', 'register-confirm-password': 'khac' })
    await submit(wrapper)

    expect(authApi.register).not.toHaveBeenCalled()
    const text = wrapper.text()
    expect(text).toContain('Số điện thoại không được để trống')
    expect(text).toContain('Mật khẩu phải từ 8 đến 100 ký tự')
    expect(text).toContain('Mật khẩu nhập lại không khớp')
  })

  it('từ chối mật khẩu thiếu chữ số hoặc thiếu chữ cái (khớp chính sách backend)', async () => {
    for (const weak of ['chiCoChuCai', '1234567890']) {
      const wrapper = mount(RegisterView)
      await fill(wrapper, { ...validForm, 'register-password': weak, 'register-confirm-password': weak })
      await submit(wrapper)
      expect(authApi.register).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('Mật khẩu phải từ 8 đến 100 ký tự')
    }
  })

  it('gửi đúng dữ liệu (không có confirmPassword/ngày sinh rỗng) rồi chuyển sang đăng nhập', async () => {
    authApi.register.mockResolvedValue({})
    const wrapper = mount(RegisterView)
    await fill(wrapper, { ...validForm, 'register-username': '  nguyenvanan  ' })
    await submit(wrapper)

    expect(authApi.register).toHaveBeenCalledTimes(1)
    const payload = authApi.register.mock.calls[0][0]
    expect(payload).toEqual({
      username: 'nguyenvanan',
      password: VALID_PASSWORD,
      fullName: 'Nguyễn Văn An',
      email: 'an@example.com',
      phone: '0912345678'
    })
    expect(hoisted.replace).toHaveBeenCalledWith({
      path: '/login',
      query: { reason: 'registered', username: 'nguyenvanan' }
    })
  })

  it('hiển thị lỗi từ server ngay tại trường tương ứng', async () => {
    authApi.register.mockRejectedValue(
      new ApiRequestError({ status: 400, code: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ', details: { username: 'Tên đăng nhập đã được sử dụng' } })
    )
    const wrapper = mount(RegisterView)
    await fill(wrapper, validForm)
    await submit(wrapper)

    expect(wrapper.text()).toContain('Tên đăng nhập đã được sử dụng')
    expect(hoisted.replace).not.toHaveBeenCalled()
  })

  it('hiển thị thông báo chung khi lỗi không gắn với trường nào (ví dụ 409)', async () => {
    authApi.register.mockRejectedValue(new ApiRequestError({ status: 409, code: 'CONFLICT', message: 'Email đã được đăng ký' }))
    const wrapper = mount(RegisterView)
    await fill(wrapper, validForm)
    await submit(wrapper)

    expect(wrapper.find('[role="alert"]').text()).toBe('Email đã được đăng ký')
  })
})

describe('Đăng nhập', () => {
  it.each([
    ['PATIENT', '/patient/dashboard'],
    ['DOCTOR', '/doctor/dashboard'],
    ['ADMIN', '/admin/dashboard']
  ])('đăng nhập vai trò %s lưu phiên và về %s', async (role, path) => {
    authApi.login.mockResolvedValue({
      token: 'jwt.token.value',
      expiresAt: new Date(Date.now() + 3600_000).toISOString(),
      userId: 7,
      username: 'user7',
      fullName: 'Người Dùng',
      roles: [role]
    })
    const wrapper = mount(LoginView)
    await fill(wrapper, { 'login-identity': '  user7  ', 'login-password': VALID_PASSWORD })
    await submit(wrapper)

    expect(authApi.login).toHaveBeenCalledWith({ usernameOrEmail: 'user7', password: VALID_PASSWORD })
    const auth = useAuthStore()
    expect(auth.token).toBe('jwt.token.value')
    expect(auth.hasRole(role)).toBe(true)
    expect(hoisted.replace).toHaveBeenCalledWith(path)
  })

  it('không gọi API khi để trống, và hiển thị lỗi của server khi sai mật khẩu', async () => {
    const wrapper = mount(LoginView)
    await submit(wrapper)
    expect(authApi.login).not.toHaveBeenCalled()
    expect(wrapper.find('[role="alert"]').text()).toContain('Vui lòng nhập đủ')

    authApi.login.mockRejectedValue(new ApiRequestError({ status: 401, code: 'UNAUTHORIZED', message: 'Tên đăng nhập hoặc mật khẩu không đúng' }))
    await fill(wrapper, { 'login-identity': 'user7', 'login-password': 'sai-mat-khau1' })
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('Tên đăng nhập hoặc mật khẩu không đúng')
    expect(useAuthStore().token).toBeNull()
    expect(hoisted.replace).not.toHaveBeenCalled()
  })

  it('hiển thị đúng thông điệp khi tài khoản bị khóa', async () => {
    authApi.login.mockRejectedValue(
      new ApiRequestError({ status: 423, code: 'ACCOUNT_LOCKED', message: 'Tài khoản tạm khóa do đăng nhập sai nhiều lần' })
    )
    const wrapper = mount(LoginView)
    await fill(wrapper, { 'login-identity': 'user7', 'login-password': VALID_PASSWORD })
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('Tài khoản tạm khóa do đăng nhập sai nhiều lần')
  })

  it.each([
    ['registered', 'Đăng ký thành công'],
    ['password-changed', 'Đã đổi mật khẩu'],
    ['expired', 'Phiên đăng nhập đã hết hạn'],
    ['logout', 'Bạn đã đăng xuất']
  ])('hiển thị thông báo cho lý do "%s"', (reason, expected) => {
    hoisted.route.query = { reason }
    expect(mount(LoginView).text()).toContain(expected)
  })

  it('điền sẵn tên đăng nhập sau khi đăng ký', () => {
    hoisted.route.query = { reason: 'registered', username: 'nguyenvanan' }
    expect(mount(LoginView).find('#login-identity').element.value).toBe('nguyenvanan')
  })
})

describe('Quên mật khẩu', () => {
  it('kiểm tra email trước khi gọi API', async () => {
    const wrapper = mount(ForgotPasswordView)
    await fill(wrapper, { 'forgot-email': 'khong-phai-email' })
    await submit(wrapper)
    expect(authApi.forgotPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Định dạng email không hợp lệ')
  })

  it('luôn báo thông điệp trung tính, không lộ email có tồn tại hay không', async () => {
    authApi.forgotPassword.mockResolvedValue({})
    const wrapper = mount(ForgotPasswordView)
    await fill(wrapper, { 'forgot-email': ' an@example.com ' })
    await submit(wrapper)
    expect(authApi.forgotPassword).toHaveBeenCalledWith({ email: 'an@example.com' })
    expect(wrapper.find('[role="status"]').text()).toContain('Nếu email đã được đăng ký')
  })
})

describe('Đặt lại mật khẩu', () => {
  it('điền sẵn mã từ liên kết và chặn mật khẩu yếu', async () => {
    hoisted.route.query = { token: 'abc123token' }
    const wrapper = mount(ResetPasswordView)
    expect(wrapper.find('#reset-token').element.value).toBe('abc123token')

    await fill(wrapper, { 'reset-password': 'yeu', 'reset-confirm-password': 'yeu' })
    await submit(wrapper)
    expect(authApi.resetPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Mật khẩu phải từ 8 đến 100 ký tự')
  })

  it('đặt lại thành công và xóa mật khẩu khỏi form', async () => {
    authApi.resetPassword.mockResolvedValue({})
    hoisted.route.query = { token: 'abc123token' }
    const wrapper = mount(ResetPasswordView)
    await fill(wrapper, { 'reset-password': VALID_PASSWORD, 'reset-confirm-password': VALID_PASSWORD })
    await submit(wrapper)

    expect(authApi.resetPassword).toHaveBeenCalledWith({ token: 'abc123token', newPassword: VALID_PASSWORD })
    expect(wrapper.find('[role="status"]').text()).toContain('Mật khẩu đã được đặt lại')
    expect(wrapper.find('#reset-password').element.value).toBe('')
  })

  it('báo lỗi khi mã hết hạn hoặc đã dùng', async () => {
    authApi.resetPassword.mockRejectedValue(new ApiRequestError({ status: 400, code: 'INVALID_TOKEN', message: 'Mã đặt lại không hợp lệ hoặc đã hết hạn' }))
    const wrapper = mount(ResetPasswordView)
    await fill(wrapper, { 'reset-token': 'het-han', 'reset-password': VALID_PASSWORD, 'reset-confirm-password': VALID_PASSWORD })
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('Mã đặt lại không hợp lệ hoặc đã hết hạn')
  })
})

describe('Đổi mật khẩu', () => {
  const loggedIn = () => {
    const auth = useAuthStore()
    auth.login({ token: 'jwt.token.value', expiresAt: new Date(Date.now() + 3600_000).toISOString(), userId: 1, username: 'u', roles: ['PATIENT'] })
    return auth
  }

  it('chặn mật khẩu mới trùng mật khẩu cũ', async () => {
    loggedIn()
    const wrapper = mount(ChangePasswordView)
    await fill(wrapper, {
      'change-old-password': VALID_PASSWORD,
      'change-new-password': VALID_PASSWORD,
      'change-confirm-password': VALID_PASSWORD
    })
    await submit(wrapper)
    expect(authApi.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Mật khẩu mới phải khác mật khẩu hiện tại')
  })

  it('đổi thành công thì xóa phiên và yêu cầu đăng nhập lại (server đã vô hiệu mọi token)', async () => {
    authApi.changePassword.mockResolvedValue({})
    const auth = loggedIn()
    const wrapper = mount(ChangePasswordView)
    await fill(wrapper, {
      'change-old-password': VALID_PASSWORD,
      'change-new-password': 'MatKhauMoi2027',
      'change-confirm-password': 'MatKhauMoi2027'
    })
    await submit(wrapper)

    expect(authApi.changePassword).toHaveBeenCalledWith({ oldPassword: VALID_PASSWORD, newPassword: 'MatKhauMoi2027' })
    expect(auth.token).toBeNull()
    expect(hoisted.replace).toHaveBeenCalledWith({ path: '/login', query: { reason: 'password-changed' } })
  })

  it('giữ phiên và báo lỗi ở trường mật khẩu hiện tại khi server từ chối', async () => {
    authApi.changePassword.mockRejectedValue(
      new ApiRequestError({ status: 400, code: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ', details: { oldPassword: 'Mật khẩu hiện tại không đúng' } })
    )
    const auth = loggedIn()
    const wrapper = mount(ChangePasswordView)
    await fill(wrapper, {
      'change-old-password': 'SaiMatKhau1',
      'change-new-password': 'MatKhauMoi2027',
      'change-confirm-password': 'MatKhauMoi2027'
    })
    await submit(wrapper)

    expect(wrapper.text()).toContain('Mật khẩu hiện tại không đúng')
    expect(auth.token).toBe('jwt.token.value')
    expect(hoisted.replace).not.toHaveBeenCalled()
  })
})
