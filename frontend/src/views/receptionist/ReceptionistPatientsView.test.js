import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ReceptionistPatientsView from './ReceptionistPatientsView.vue'
import PatientFormModal from './PatientFormModal.vue'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  toastSuccess: vi.fn()
}))

vi.mock('@/api/reception', () => ({
  receptionApi: {
    patients: {
      list: mocks.list,
      create: mocks.create
    }
  }
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess })
}))

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
const patient = { id: 1, patientCode: 'BN000001', fullName: 'Nguyễn Minh An', dateOfBirth: '1990-01-01', genderCode: 'MALE', phone: '0900111222', hasAccount: false }

const global = {
  stubs: {
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
    UserMenu: { template: '<div />' },
    NotificationBell: { template: '<div />' }
  }
}

function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  document.body.innerHTML = ''
  mocks.list.mockResolvedValue(page([patient]))
  mocks.create.mockResolvedValue(patient)
}

describe('ReceptionistPatientsView', () => {
  beforeEach(resetMocks)

  it('tải danh sách, hiện cột tài khoản theo hasAccount và nút đặt lịch', async () => {
    const wrapper = mount(ReceptionistPatientsView, { global, attachTo: document.body })
    await flushPromises()

    expect(mocks.list).toHaveBeenCalledWith({ page: 0, size: 20 })
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).toContain('Nam')
    expect(wrapper.text()).toContain('Không')
    expect(wrapper.find('a[href="/receptionist/booking?patientId=1"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('tìm gọi patients.list với keyword, page và size', async () => {
    const wrapper = mount(ReceptionistPatientsView, { global })
    await flushPromises()
    await wrapper.find('#patient-keyword').setValue('An')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mocks.list).toHaveBeenLastCalledWith({ keyword: 'An', page: 0, size: 20 })
  })

  it('có trạng thái rỗng và lỗi kèm nút thử lại', async () => {
    mocks.list.mockResolvedValueOnce(page([]))
    const wrapper = mount(ReceptionistPatientsView, { global })
    await flushPromises()
    expect(wrapper.text()).toContain('Chưa có bệnh nhân phù hợp')

    mocks.list.mockRejectedValueOnce(Object.assign(new Error('Mất kết nối'), { status: 0 }))
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Mất kết nối')

    mocks.list.mockResolvedValueOnce(page([patient]))
    await wrapper.findAll('button').find((button) => button.text() === 'Thử lại').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Nguyễn Minh An')
  })

  it('nhận bệnh nhân mới từ biểu mẫu thì thêm vào bảng', async () => {
    const wrapper = mount(ReceptionistPatientsView, { global, attachTo: document.body })
    await flushPromises()
    wrapper.findComponent(PatientFormModal).vm.$emit('created', { ...patient, id: 2, patientCode: 'BN000002', fullName: 'Trần Gia Bình', hasAccount: true })
    await flushPromises()

    expect(wrapper.text()).toContain('Trần Gia Bình')
    expect(wrapper.text()).toContain('Có')
    wrapper.unmount()
  })
})
