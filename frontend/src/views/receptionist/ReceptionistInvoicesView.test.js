import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ReceptionistInvoicesView from './ReceptionistInvoicesView.vue'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  items: vi.fn(),
  collect: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}))

vi.mock('@/api/reception', () => ({
  receptionApi: {
    invoices: {
      list: mocks.list,
      items: mocks.items,
      collect: mocks.collect
    }
  }
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError })
}))

const unpaid = { id: 1, invoiceCode: 'INV-001', patientId: 1, patientName: 'Nguyễn Minh An', patientCode: 'BN000001', subtotal: 150000, discountAmount: 0, totalAmount: 150000, status: 'UNPAID', issuedAt: '2026-10-09T08:00:00' }
const paid = { ...unpaid, id: 2, invoiceCode: 'INV-002', patientName: 'Trần Gia Bình', patientCode: 'BN000002', status: 'PAID', paidAt: '2026-10-09T09:00:00' }
const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

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
  mocks.list.mockResolvedValue(page([unpaid, paid]))
  mocks.items.mockResolvedValue([{ id: 1, serviceId: 1, description: 'Khám tổng quát', quantity: 1, unitPrice: 150000, lineTotal: 150000 }])
  mocks.collect.mockResolvedValue({ ...unpaid, status: 'PAID', paidAt: '2026-10-09T10:00:00' })
}

async function mountPage() {
  const wrapper = mount(ReceptionistInvoicesView, { global, attachTo: document.body })
  await flushPromises()
  return wrapper
}

async function clickInDialog(text) {
  const dialogs = document.querySelectorAll('[role="dialog"]')
  const scope = dialogs[dialogs.length - 1] || document
  const button = Array.from(scope.querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(button, text).toBeTruthy()
  button.click()
  await flushPromises()
}

describe('ReceptionistInvoicesView', () => {
  beforeEach(resetMocks)

  it('mở trang gọi invoices.list với status UNPAID và hiện dữ liệu', async () => {
    const wrapper = await mountPage()
    expect(mocks.list).toHaveBeenCalledWith({ status: 'UNPAID', page: 0, size: 20 })
    expect(wrapper.text()).toContain('INV-001')
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).toContain('150.000')
    wrapper.unmount()
  })

  it('đổi bộ lọc gửi đúng tham số và về trang đầu', async () => {
    const wrapper = await mountPage()
    await wrapper.find('#invoice-status').setValue('PAID')
    await wrapper.find('#invoice-keyword').setValue('INV')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith({ status: 'PAID', keyword: 'INV', page: 0, size: 20 })
    wrapper.unmount()
  })

  it('có trạng thái rỗng và lỗi tải danh sách kèm thử lại', async () => {
    mocks.list.mockResolvedValueOnce(page([]))
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain('Chưa có hóa đơn phù hợp')

    mocks.list.mockRejectedValueOnce(Object.assign(new Error('Mất kết nối'), { status: 0 }))
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Mất kết nối')
    mocks.list.mockResolvedValueOnce(page([unpaid]))
    await wrapper.findAll('button').find((button) => button.text() === 'Thử lại').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('INV-001')
    wrapper.unmount()
  })

  it('hộp chi tiết hiện các dòng và ba số tổng; lỗi tải dòng có nút thử lại', async () => {
    mocks.items.mockRejectedValueOnce(Object.assign(new Error('Không tải được dòng hóa đơn'), { status: 0 }))
    const wrapper = await mountPage()
    await wrapper.findAll('button').find((button) => button.text() === 'INV-001').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Không tải được dòng hóa đơn')
    mocks.items.mockResolvedValueOnce([{ id: 1, serviceId: 1, description: 'Khám tổng quát', quantity: 1, unitPrice: 150000, lineTotal: 150000 }])
    await clickInDialog('Thử lại')
    expect(document.body.textContent).toContain('Khám tổng quát')
    expect(document.body.textContent).toContain('Tạm tính')
    expect(document.body.textContent).toContain('Giảm giá')
    expect(document.body.textContent).toContain('Tổng tiền')
    wrapper.unmount()
  })

  it('nút Thu tiền chỉ có ở UNPAID; hủy xác nhận không gọi API', async () => {
    const wrapper = await mountPage()
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Thu tiền')).toHaveLength(1)
    await wrapper.find('button[aria-label="Thu tiền INV-001"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('150.000')
    await clickInDialog('Hủy')
    expect(mocks.collect).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('thu thành công xóa dòng khi đang lọc chưa thu và khóa gửi lặp', async () => {
    let resolveCollect
    mocks.collect.mockReturnValueOnce(new Promise((resolve) => { resolveCollect = resolve }))
    const wrapper = await mountPage()
    await wrapper.find('button[aria-label="Thu tiền INV-001"]').trigger('click')
    await flushPromises()
    const confirm = Array.from(document.querySelectorAll('[role="dialog"] button')).find((button) => button.textContent.trim() === 'Đã thu')
    confirm.click()
    confirm.click()
    expect(mocks.collect).toHaveBeenCalledTimes(1)
    resolveCollect({ ...unpaid, status: 'PAID', paidAt: '2026-10-09T10:00:00' })
    await flushPromises()
    expect(mocks.toastSuccess).toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('INV-001')
    wrapper.unmount()
  })

  it('thu bị 409 hiện thông điệp và tải lại danh sách', async () => {
    mocks.collect.mockRejectedValueOnce(Object.assign(new Error('Hóa đơn đã được xử lý'), { status: 409 }))
    const wrapper = await mountPage()
    await wrapper.find('button[aria-label="Thu tiền INV-001"]').trigger('click')
    await flushPromises()
    await clickInDialog('Đã thu')
    expect(mocks.toastError).toHaveBeenCalledWith('Hóa đơn đã được xử lý')
    expect(mocks.list).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
