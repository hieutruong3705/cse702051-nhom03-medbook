import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError } from '@/api/http'
import { attachmentsApi } from '@/api/attachments'
import { encountersApi } from '@/api/encounters'
import { invoicesApi } from '@/api/invoices'
import { prescriptionItemsApi } from '@/api/prescriptionItems'
import { prescriptionsApi } from '@/api/prescriptions'
import PatientEncounterDetail from './PatientEncounterDetail.vue'

const hoisted = vi.hoisted(() => ({ route: { params: { encounterId: '9' } }, saveBlob: vi.fn() }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => hoisted.route,
    useRouter: () => ({ push: vi.fn(), replace: vi.fn(), resolve: () => ({ matched: [] }) })
  }
})

vi.mock('@/api/encounters', () => ({ encountersApi: { get: vi.fn() } }))
vi.mock('@/api/prescriptions', () => ({ prescriptionsApi: { listByEncounter: vi.fn() } }))
vi.mock('@/api/prescriptionItems', () => ({ prescriptionItemsApi: { listByPrescription: vi.fn() } }))
vi.mock('@/api/attachments', () => ({ attachmentsApi: { listByEncounter: vi.fn(), download: vi.fn() } }))
vi.mock('@/api/invoices', () => ({ invoicesApi: { byEncounter: vi.fn() } }))
vi.mock('@/views/doctor/encounter/encounterUtils', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, saveBlob: hoisted.saveBlob }
})

const apiError = (status, message = 'Lỗi') => new ApiRequestError({ status, message })

const completed = (overrides = {}) => ({
  id: 9,
  status: 'COMPLETED',
  doctorName: 'BS. Nguyễn Thị Lan',
  encounterAt: '2026-10-02T09:00:00',
  chiefComplaint: 'Ho và sốt nhẹ',
  diagnosis: 'Viêm họng cấp',
  clinicalNotes: 'Họng đỏ',
  treatmentPlan: 'Nghỉ ngơi, uống nhiều nước',
  followUpNote: 'Tái khám sau 5 ngày',
  ...overrides
})

async function mountView() {
  const wrapper = mount(PatientEncounterDetail, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  hoisted.route.params = { encounterId: '9' }
  encountersApi.get.mockResolvedValue(completed())
  prescriptionsApi.listByEncounter.mockResolvedValue([
    { id: 20, prescriptionCode: 'RX-AB12', issuedAt: '2026-10-02T09:30:00', notes: 'Uống sau ăn' }
  ])
  prescriptionItemsApi.listByPrescription.mockResolvedValue([
    { id: 31, medicineName: 'Paracetamol 500mg', quantity: 10, dosage: '1 viên', frequency: '2 lần/ngày', durationDays: 5 }
  ])
  attachmentsApi.listByEncounter.mockResolvedValue([{ id: 7, originalFileName: 'xet-nghiem.pdf' }])
  invoicesApi.byEncounter.mockResolvedValue({ id: 5, invoiceCode: 'INV-1', status: 'UNPAID', totalAmount: 350000 })
})

describe('PatientEncounterDetail', () => {
  it('hiển thị kết quả khám, đơn thuốc, tệp và hóa đơn của lần khám', async () => {
    const wrapper = await mountView()

    expect(encountersApi.get).toHaveBeenCalledWith('9')
    expect(wrapper.find('[data-test="field-diagnosis"]').text()).toContain('Viêm họng cấp')
    expect(wrapper.find('[data-test="field-treatmentPlan"]').text()).toContain('Nghỉ ngơi, uống nhiều nước')
    expect(wrapper.text()).toContain('BS. Nguyễn Thị Lan')
    expect(wrapper.find('[data-test="open-note"]').exists()).toBe(false)

    const item = wrapper.find('[data-test="item-31"]').text()
    expect(item).toContain('Paracetamol 500mg')
    expect(item).toContain('1 viên · 2 lần/ngày · 5 ngày')
    expect(wrapper.find('[data-test="attachment-7"]').text()).toContain('xet-nghiem.pdf')
    expect(wrapper.find('[data-test="invoice"]').text()).toContain('INV-1')
    expect(wrapper.find('[data-test="invoice"]').text()).toContain('Chưa thu')
    wrapper.unmount()
  })

  it('lần khám chưa hoàn thành có ghi chú nội dung còn có thể thay đổi', async () => {
    encountersApi.get.mockResolvedValue(completed({ status: 'OPEN' }))
    const wrapper = await mountView()
    expect(wrapper.find('[data-test="open-note"]').text()).toContain('chưa hoàn thành')
    wrapper.unmount()
  })

  it('chưa có đơn thuốc, tệp, hóa đơn thì hiện trạng thái trống (404 hóa đơn không phải lỗi)', async () => {
    prescriptionsApi.listByEncounter.mockResolvedValue([])
    attachmentsApi.listByEncounter.mockResolvedValue([])
    invoicesApi.byEncounter.mockRejectedValue(apiError(404, 'Lần khám này chưa có hóa đơn'))
    const wrapper = await mountView()

    expect(wrapper.find('[data-test="rx-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="att-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="inv-empty"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it.each([[403], [404]])('lỗi %i: không lộ dữ liệu, chỉ báo không tìm thấy trong bệnh án của bạn', async (status) => {
    encountersApi.get.mockRejectedValue(apiError(status))
    const wrapper = await mountView()

    const alert = wrapper.find('[data-test="load-error"]')
    expect(alert.text()).toContain('Không tìm thấy lần khám này trong bệnh án của bạn')
    expect(alert.find('button').exists()).toBe(false)
    expect(prescriptionsApi.listByEncounter).not.toHaveBeenCalled()
    expect(attachmentsApi.listByEncounter).not.toHaveBeenCalled()
    expect(invoicesApi.byEncounter).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('lỗi mạng/máy chủ cho phép thử lại', async () => {
    encountersApi.get.mockRejectedValueOnce(apiError(500))
    const wrapper = await mountView()
    const retry = wrapper.find('[data-test="load-error"] button')
    expect(retry.exists()).toBe(true)

    await retry.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="field-diagnosis"]').text()).toContain('Viêm họng cấp')
    wrapper.unmount()
  })

  it('tải tệp đính kèm bằng blob và giữ tên gốc; lỗi tải hiện thông báo', async () => {
    const blob = new Blob(['%PDF'])
    attachmentsApi.download.mockResolvedValueOnce(blob)
    const wrapper = await mountView()

    await wrapper.find('[data-test="attachment-7"] button').trigger('click')
    await flushPromises()
    expect(attachmentsApi.download).toHaveBeenCalledWith(7)
    expect(hoisted.saveBlob).toHaveBeenCalledWith(blob, 'xet-nghiem.pdf')

    attachmentsApi.download.mockRejectedValueOnce(apiError(403))
    await wrapper.find('[data-test="attachment-7"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="download-error"]').text()).toContain('không có quyền')
    wrapper.unmount()
  })

  it('lỗi riêng của đơn thuốc không làm mất phần còn lại của trang', async () => {
    prescriptionsApi.listByEncounter.mockRejectedValue(apiError(500))
    const wrapper = await mountView()

    expect(wrapper.find('[data-test="prescriptions"]').text()).toContain('Hệ thống đang bận')
    expect(wrapper.find('[data-test="field-diagnosis"]').text()).toContain('Viêm họng cấp')
    expect(wrapper.find('[data-test="attachment-7"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
