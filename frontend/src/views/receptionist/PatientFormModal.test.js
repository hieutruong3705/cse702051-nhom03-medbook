import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import PatientFormModal from './PatientFormModal.vue'

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  toastSuccess: vi.fn()
}))

vi.mock('@/api/reception', () => ({
  receptionApi: { patients: { create: mocks.create } }
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess })
}))

function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  document.body.innerHTML = ''
  mocks.create.mockResolvedValue({
    id: 1,
    patientCode: 'BN000001',
    fullName: 'Nguyễn Minh An',
    dateOfBirth: '1990-01-01',
    genderCode: 'MALE',
    phone: '0900111222',
    hasAccount: false
  })
}

function mountModal() {
  return mount(PatientFormModal, { props: { modelValue: true }, attachTo: document.body })
}

function type(selector, value, event = 'input') {
  const element = document.querySelector(selector)
  expect(element, selector).toBeTruthy()
  element.value = value
  element.dispatchEvent(new Event(event))
}

async function submit() {
  const button = Array.from(document.querySelectorAll('button')).find((item) => item.textContent.trim() === 'Lưu')
  expect(button).toBeTruthy()
  button.click()
  await flushPromises()
}

describe('PatientFormModal', () => {
  beforeEach(resetMocks)

  it('không gửi khi thiếu trường bắt buộc, ngày sinh tương lai hoặc điện thoại sai', async () => {
    const wrapper = mountModal()
    type('#patient-date-of-birth', '2999-01-01', 'input')
    type('#patient-phone', 'abc')
    await submit()

    expect(mocks.create).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Họ tên là bắt buộc')
    expect(document.body.textContent).toContain('Ngày sinh không được ở tương lai')
    expect(document.body.textContent).toContain('Điện thoại phải có 8-30 ký tự hợp lệ')
    wrapper.unmount()
  })

  it('400 từ máy chủ hiện lỗi theo trường', async () => {
    mocks.create.mockRejectedValueOnce(Object.assign(new Error('Dữ liệu không hợp lệ'), { status: 400, details: { email: 'Email không hợp lệ' } }))
    const wrapper = mountModal()
    type('#patient-full-name', 'Nguyễn Minh An')
    type('#patient-date-of-birth', '1990-01-01', 'input')
    type('#patient-gender', 'MALE', 'change')
    type('#patient-phone', '0900111222')
    type('#patient-email', 'sai@example.com')
    await submit()

    expect(mocks.create).toHaveBeenCalled()
    expect(document.querySelector('#patient-email-error').textContent).toContain('Email không hợp lệ')
    wrapper.unmount()
  })

  it('409 hiện lỗi tại ô điện thoại kèm gợi ý tìm hồ sơ đã có', async () => {
    mocks.create.mockRejectedValueOnce(Object.assign(new Error('Hồ sơ đã tồn tại'), { status: 409, details: { phone: 'Đã có hồ sơ trùng' } }))
    const wrapper = mountModal()
    type('#patient-full-name', 'Nguyễn Minh An')
    type('#patient-date-of-birth', '1990-01-01', 'input')
    type('#patient-gender', 'MALE', 'change')
    type('#patient-phone', '0900111222')
    await submit()

    expect(document.querySelector('#patient-phone-error').textContent).toContain('Đã có hồ sơ trùng')
    expect(document.body.textContent).toContain('Hồ sơ đã tồn tại')
    wrapper.unmount()
  })

  it('tạo thành công thì đóng modal, báo toast và phát sự kiện created', async () => {
    const wrapper = mountModal()
    type('#patient-full-name', 'Nguyễn Minh An')
    type('#patient-date-of-birth', '1990-01-01', 'input')
    type('#patient-gender', 'MALE', 'change')
    type('#patient-phone', '0900111222')
    type('#patient-email', 'an@example.com')
    type('#patient-address', 'Quận 1')
    await submit()

    expect(mocks.create).toHaveBeenCalledWith({
      fullName: 'Nguyễn Minh An',
      dateOfBirth: '1990-01-01',
      genderCode: 'MALE',
      phone: '0900111222',
      email: 'an@example.com',
      address: 'Quận 1'
    })
    expect(mocks.toastSuccess).toHaveBeenCalledWith('Đã thêm bệnh nhân')
    expect(wrapper.emitted('created')[0][0]).toMatchObject({ id: 1 })
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([false])
    wrapper.unmount()
  })
})
