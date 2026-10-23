import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError } from '@/api/http'
import { attachmentsApi } from '@/api/attachments'
import { catalogApi } from '@/api/catalog'
import { encountersApi } from '@/api/encounters'
import { invoicesApi } from '@/api/invoices'
import { prescriptionItemsApi } from '@/api/prescriptionItems'
import { prescriptionsApi } from '@/api/prescriptions'
import { recordsApi } from '@/api/records'
import DoctorEncounterView from './DoctorEncounterView.vue'
import EncounterAttachmentsTab from './EncounterAttachmentsTab.vue'
import EncounterExamTab from './EncounterExamTab.vue'
import EncounterInvoiceTab from './EncounterInvoiceTab.vue'
import EncounterPrescriptionsTab from './EncounterPrescriptionsTab.vue'
import { buildChanges, serverFieldErrors, toFormValues, validateLengths } from './encounterUtils'

const hoisted = vi.hoisted(() => ({
  route: { params: { id: '9' } },
  push: vi.fn(),
  replace: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
  saveBlob: vi.fn()
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => hoisted.route,
    useRouter: () => ({ push: hoisted.push, replace: hoisted.replace, resolve: () => ({ matched: [] }) }),
    onBeforeRouteLeave: vi.fn()
  }
})

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: hoisted.toastSuccess, error: hoisted.toastError })
}))

vi.mock('@/api/encounters', () => ({ encountersApi: { get: vi.fn(), update: vi.fn() } }))
vi.mock('@/api/records', () => ({ recordsApi: { get: vi.fn() } }))
vi.mock('@/api/prescriptions', () => ({ prescriptionsApi: { listByEncounter: vi.fn(), createForEncounter: vi.fn() } }))
vi.mock('@/api/prescriptionItems', () => ({ prescriptionItemsApi: { listByPrescription: vi.fn(), create: vi.fn(), remove: vi.fn() } }))
vi.mock('@/api/attachments', () => ({ attachmentsApi: { listByEncounter: vi.fn(), upload: vi.fn(), download: vi.fn(), remove: vi.fn() } }))
vi.mock('@/api/invoices', () => ({ invoicesApi: { byEncounter: vi.fn(), items: vi.fn(), createForEncounter: vi.fn() } }))
vi.mock('@/api/catalog', () => ({ catalogApi: { services: { list: vi.fn() }, medicines: { list: vi.fn() } } }))

vi.mock('./encounterUtils', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, saveBlob: hoisted.saveBlob }
})

const apiError = (status, message = 'Lỗi', details = {}) => new ApiRequestError({ status, message, details })

const encounter = (overrides = {}) => ({
  id: 9,
  appointmentId: 55,
  medicalRecordId: 3,
  patientId: 4,
  patientName: 'Nguyễn Văn An',
  doctorId: 1,
  doctorName: 'BS. Lan',
  encounterAt: '2026-10-02T09:00:00',
  chiefComplaint: 'Ho kéo dài',
  diagnosis: '',
  clinicalNotes: '',
  treatmentPlan: '',
  followUpNote: '',
  status: 'OPEN',
  editable: true,
  ...overrides
})

const byText = (root, text) => [...root.querySelectorAll('button')].find((button) => button.textContent.trim() === text)
/** Nút trong hộp thoại xác nhận (Teleport ra body), tránh nhầm với nút cùng tên ở biểu mẫu. */
const inDialog = (text) => byText(document.body.querySelector('[role="dialog"]'), text)

beforeEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  hoisted.route.params = { id: '9' }
  encountersApi.get.mockResolvedValue(encounter())
  recordsApi.get.mockResolvedValue({ id: 3, bloodType: null, allergyNotes: null })
  prescriptionsApi.listByEncounter.mockResolvedValue([])
  attachmentsApi.listByEncounter.mockResolvedValue([])
  invoicesApi.byEncounter.mockRejectedValue(apiError(404, 'Lần khám này chưa có hóa đơn'))
  catalogApi.services.list.mockResolvedValue([{ id: 1, name: 'Khám tổng quát', price: 200000 }, { id: 2, name: 'Siêu âm', price: 250000 }])
  // Mặc định danh mục thuốc trống: biểu mẫu kê đơn dùng ô nhập tên. Test về danh mục tự đặt dữ liệu.
  catalogApi.medicines.list.mockResolvedValue([])
})

afterEach(() => {
  vi.useRealTimers()
})

describe('encounterUtils', () => {
  it('chỉ gửi các trường đã đổi và bỏ nhóm máu rỗng', () => {
    const baseline = toFormValues(encounter({ chiefComplaint: 'Ho' }), { bloodType: 'O+', allergyNotes: 'Không' })
    expect(buildChanges({ ...baseline }, baseline)).toEqual({})

    expect(buildChanges({ ...baseline, diagnosis: 'Viêm họng' }, baseline)).toEqual({ diagnosis: 'Viêm họng' })
    // xóa nội dung = gửi chuỗi rỗng để server xóa
    expect(buildChanges({ ...baseline, chiefComplaint: '' }, baseline)).toEqual({ chiefComplaint: '' })
    // tóm tắt bệnh án chỉ gửi trường đã đổi, gom trong clinicalSummary
    expect(buildChanges({ ...baseline, allergyNotes: 'Penicillin' }, baseline)).toEqual({
      clinicalSummary: { allergyNotes: 'Penicillin' }
    })
    // nhóm máu rỗng không hợp lệ với mẫu A/B/AB/O nên không gửi
    expect(buildChanges({ ...baseline, bloodType: '' }, baseline)).toEqual({})
  })

  it('kiểm độ dài theo giới hạn của backend và ánh xạ lỗi server về từng trường', () => {
    const values = toFormValues(encounter(), null)
    expect(validateLengths(values)).toEqual({})
    expect(validateLengths({ ...values, followUpNote: 'x'.repeat(2001) }).followUpNote).toContain('2000')
    expect(serverFieldErrors({ diagnosis: 'Bắt buộc', 'clinicalSummary.bloodType': 'Sai' })).toEqual({
      diagnosis: 'Bắt buộc',
      bloodType: 'Sai'
    })
  })
})

describe('EncounterExamTab', () => {
  const mountTab = async (props = {}) => {
    const wrapper = mount(EncounterExamTab, { props: { encounter: encounter(), autosaveMs: 0, ...props }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  it('lưu nháp chỉ gửi trường đã đổi và phát sự kiện saved', async () => {
    const saved = encounter({ diagnosis: 'Viêm họng cấp' })
    encountersApi.update.mockResolvedValue(saved)
    const wrapper = await mountTab()

    expect(wrapper.find('[data-test="save-button"]').attributes('disabled')).toBeDefined()
    await wrapper.find('#encounter-diagnosis').setValue('Viêm họng cấp')
    expect(wrapper.find('[data-test="save-status"]').text()).toContain('chưa lưu')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(encountersApi.update).toHaveBeenCalledWith(9, { diagnosis: 'Viêm họng cấp' })
    expect(wrapper.emitted('saved')[0][0]).toEqual(saved)
    expect(wrapper.find('[data-test="save-status"]').text()).toContain('Đã lưu')
    wrapper.unmount()
  })

  it('điền sẵn tóm tắt bệnh án và chỉ gửi clinicalSummary khi có thay đổi', async () => {
    recordsApi.get.mockResolvedValue({ id: 3, bloodType: 'A+', allergyNotes: 'Penicillin' })
    encountersApi.update.mockResolvedValue(encounter())
    const wrapper = await mountTab()

    expect(recordsApi.get).toHaveBeenCalledWith(3)
    expect(wrapper.find('#summary-bloodType').element.value).toBe('A+')
    expect(wrapper.find('#summary-allergyNotes').element.value).toBe('Penicillin')

    await wrapper.find('#encounter-clinicalNotes').setValue('Họng đỏ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(encountersApi.update).toHaveBeenLastCalledWith(9, { clinicalNotes: 'Họng đỏ' })

    await wrapper.find('#summary-bloodType').setValue('B-')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(encountersApi.update).toHaveBeenLastCalledWith(9, { clinicalSummary: { bloodType: 'B-' } })
    wrapper.unmount()
  })

  it('không cho hoàn thành khi thiếu chẩn đoán', async () => {
    const wrapper = await mountTab()
    await wrapper.find('[data-test="complete-button"]').trigger('click')
    await flushPromises()

    expect(encountersApi.update).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="form-error"]').text()).toContain('thiếu chẩn đoán')
    expect(wrapper.text()).toContain('Phải nhập chẩn đoán')
    expect(document.body.textContent).not.toContain('Hoàn thành khám?')
    wrapper.unmount()
  })

  it('hoàn thành khám: xác nhận rồi gửi các thay đổi kèm status COMPLETED', async () => {
    const done = encounter({ diagnosis: 'Viêm họng', status: 'COMPLETED', editable: false })
    encountersApi.update.mockResolvedValue(done)
    const wrapper = await mountTab()

    await wrapper.find('#encounter-diagnosis').setValue('Viêm họng')
    await wrapper.find('[data-test="complete-button"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Hoàn thành khám?')
    expect(encountersApi.update).not.toHaveBeenCalled()

    inDialog('Hoàn thành khám').click()
    await flushPromises()

    expect(encountersApi.update).toHaveBeenCalledWith(9, { diagnosis: 'Viêm họng', status: 'COMPLETED' })
    expect(wrapper.emitted('saved')[0][0]).toEqual(done)
    expect(hoisted.toastSuccess).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('lỗi 400 của server hiện ngay dưới trường tương ứng', async () => {
    encountersApi.update.mockRejectedValue(apiError(400, 'Dữ liệu không hợp lệ', { 'clinicalSummary.bloodType': 'Nhóm máu không hợp lệ' }))
    const wrapper = await mountTab()
    await wrapper.find('#summary-bloodType').setValue('A+')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Nhóm máu không hợp lệ')
    expect(wrapper.find('[data-test="form-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="save-status"]').text()).toContain('không thành công')
    wrapper.unmount()
  })

  it('409 khi lưu: báo lỗi và phát stale để trang tải lại sang chế độ chỉ đọc', async () => {
    encountersApi.update.mockRejectedValue(apiError(409, 'Lần khám đã hoàn thành'))
    const wrapper = await mountTab()
    await wrapper.find('#encounter-diagnosis').setValue('X')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('stale')).toHaveLength(1)
    expect(wrapper.find('[data-test="form-error"]').text()).toContain('Lần khám đã hoàn thành')
    wrapper.unmount()
  })

  it('lưu nháp tự động sau khi ngừng gõ, chỉ một lần', async () => {
    vi.useFakeTimers()
    encountersApi.update.mockResolvedValue(encounter({ diagnosis: 'Cảm cúm' }))
    const wrapper = mount(EncounterExamTab, { props: { encounter: encounter(), autosaveMs: 1000 }, attachTo: document.body })
    await vi.advanceTimersByTimeAsync(0)

    await wrapper.find('#encounter-diagnosis').setValue('Cảm')
    await vi.advanceTimersByTimeAsync(600)
    await wrapper.find('#encounter-diagnosis').setValue('Cảm cúm')
    await vi.advanceTimersByTimeAsync(600) // chưa đủ 1000ms kể từ lần gõ cuối
    expect(encountersApi.update).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(600)
    expect(encountersApi.update).toHaveBeenCalledTimes(1)
    expect(encountersApi.update).toHaveBeenCalledWith(9, { diagnosis: 'Cảm cúm' })
    await vi.advanceTimersByTimeAsync(5000)
    expect(encountersApi.update).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('lần khám đã hoàn thành: các ô bị khóa và không có nút lưu/hoàn thành', async () => {
    const wrapper = await mountTab({ encounter: encounter({ status: 'COMPLETED', editable: false, diagnosis: 'Đã khám' }) })

    expect(wrapper.find('[data-test="readonly-banner"]').text()).toContain('đã hoàn thành')
    expect(wrapper.find('#encounter-diagnosis').attributes('disabled')).toBeDefined()
    expect(wrapper.find('#summary-bloodType').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-test="save-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="complete-button"]').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('DoctorEncounterView', () => {
  const mountView = async () => {
    const wrapper = mount(DoctorEncounterView, { props: { autosaveMs: 0 }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  it('tải lần khám theo id trên đường dẫn và hiện tóm tắt cùng các tab', async () => {
    const wrapper = await mountView()
    expect(encountersApi.get).toHaveBeenCalledWith('9')
    const summary = wrapper.find('[data-test="encounter-summary"]')
    expect(summary.text()).toContain('Nguyễn Văn An')
    expect(summary.text()).toContain('#55')
    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(['Khám', 'Đơn thuốc', 'Tệp đính kèm', 'Hóa đơn'])
    expect(wrapper.find('#encounter-diagnosis').exists()).toBe(true)
    wrapper.unmount()
  })

  it('chỉ tải dữ liệu của tab khi mở lần đầu', async () => {
    const wrapper = await mountView()
    expect(prescriptionsApi.listByEncounter).not.toHaveBeenCalled()
    expect(attachmentsApi.listByEncounter).not.toHaveBeenCalled()

    await wrapper.find('[data-test="tab-prescriptions"]').trigger('click')
    await flushPromises()
    expect(prescriptionsApi.listByEncounter).toHaveBeenCalledWith(9)
    expect(attachmentsApi.listByEncounter).not.toHaveBeenCalled()

    await wrapper.find('[data-test="tab-attachments"]').trigger('click')
    await flushPromises()
    expect(attachmentsApi.listByEncounter).toHaveBeenCalledWith(9)
    wrapper.unmount()
  })

  it.each([
    [403, 'không phụ trách', false],
    [404, 'Không tìm thấy lần khám', false],
    [500, 'Hệ thống đang bận', true]
  ])('lỗi %i hiện thông báo phù hợp (nút thử lại: %s)', async (status, text, canRetry) => {
    encountersApi.get.mockRejectedValue(apiError(status, 'Lỗi'))
    const wrapper = await mountView()
    const alert = wrapper.find('[data-test="load-error"]')
    expect(alert.text()).toContain(text)
    expect(alert.find('button').exists()).toBe(canRetry)
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('sau khi lưu xong hoàn thành khám thì chuyển sang chế độ chỉ đọc ở mọi tab', async () => {
    encountersApi.update.mockResolvedValue(encounter({ diagnosis: 'Viêm họng', status: 'COMPLETED', editable: false }))
    const wrapper = await mountView()
    await wrapper.find('[data-test="tab-prescriptions"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="create-prescription"]').exists()).toBe(true)

    await wrapper.find('[data-test="tab-exam"]').trigger('click')
    await wrapper.find('#encounter-diagnosis').setValue('Viêm họng')
    await wrapper.find('[data-test="complete-button"]').trigger('click')
    await flushPromises()
    inDialog('Hoàn thành khám').click()
    await flushPromises()

    expect(wrapper.find('[data-test="readonly-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="create-prescription"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="encounter-summary"]').text()).toContain('Hoàn thành')
    wrapper.unmount()
  })
})

describe('EncounterPrescriptionsTab', () => {
  const RX = [{ id: 20, prescriptionCode: 'RX-AB12', issuedAt: '2026-10-02T09:30:00', status: 'ACTIVE', notes: 'Uống sau ăn' }]
  const ITEMS = [{ id: 31, medicineName: 'Paracetamol 500mg', dosage: '1 viên', frequency: '2 lần/ngày', durationDays: 5, quantity: 10 }]

  const mountTab = async (editable = true) => {
    prescriptionsApi.listByEncounter.mockResolvedValue(RX)
    prescriptionItemsApi.listByPrescription.mockResolvedValue(ITEMS)
    const wrapper = mount(EncounterPrescriptionsTab, { props: { encounterId: 9, editable }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  it('hiện đơn và dòng thuốc; bác sĩ phụ trách thấy biểu mẫu thêm thuốc', async () => {
    const wrapper = await mountTab()
    expect(prescriptionItemsApi.listByPrescription).toHaveBeenCalledWith(20)
    expect(wrapper.find('[data-test="prescription-20"]').text()).toContain('RX-AB12')
    expect(wrapper.find('[data-test="item-31"]').text()).toContain('Paracetamol 500mg')
    expect(wrapper.find('[data-test="item-form-20"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('chế độ chỉ đọc: không có nút tạo đơn, biểu mẫu hay nút xóa', async () => {
    const wrapper = await mountTab(false)
    expect(wrapper.find('[data-test="item-31"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="create-prescription"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="item-form-20"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Xóa')
    wrapper.unmount()
  })

  it('kiểm tra dữ liệu trước khi gửi: thiếu tên thuốc, số lượng không hợp lệ', async () => {
    const wrapper = await mountTab()
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    await flushPromises()
    expect(prescriptionItemsApi.create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Phải nhập tên thuốc')
    expect(wrapper.text()).toContain('Số lượng phải lớn hơn 0')

    await wrapper.find('#rx-20-name').setValue('Amoxicillin')
    await wrapper.find('#rx-20-quantity').setValue('3')
    await wrapper.find('#rx-20-days').setValue('0')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    await flushPromises()
    expect(prescriptionItemsApi.create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Số ngày phải là số nguyên dương')
    wrapper.unmount()
  })

  it('thêm thuốc: gửi dữ liệu đã chuẩn hóa, thêm dòng mới và xóa biểu mẫu', async () => {
    const created = { id: 32, medicineName: 'Amoxicillin', quantity: 21, durationDays: 7, dosage: '1 viên' }
    prescriptionItemsApi.create.mockResolvedValue(created)
    const wrapper = await mountTab()

    await wrapper.find('#rx-20-name').setValue('  Amoxicillin  ')
    await wrapper.find('#rx-20-quantity').setValue('21')
    await wrapper.find('#rx-20-days').setValue('7')
    await wrapper.find('#rx-20-dosage').setValue('1 viên')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    await flushPromises()

    expect(prescriptionItemsApi.create).toHaveBeenCalledWith(20, {
      medicineName: 'Amoxicillin',
      quantity: 21,
      durationDays: 7,
      dosage: '1 viên',
      frequency: undefined,
      instructions: undefined
    })
    expect(wrapper.find('[data-test="item-32"]').exists()).toBe(true)
    expect(wrapper.find('#rx-20-name').element.value).toBe('')
    wrapper.unmount()
  })

  it('có danh mục thuốc: bắt buộc chọn thuốc và gửi medicineId, không gửi tên tự nhập', async () => {
    catalogApi.medicines.list.mockResolvedValue({ content: [{ id: 2, name: 'Amoxicillin 500mg', unit: 'Viên' }, { id: 5, name: 'Vitamin C 500mg', unit: '' }] })
    prescriptionItemsApi.create.mockResolvedValue({ id: 33, medicineId: 2, medicineName: 'Amoxicillin 500mg', quantity: 14 })
    const wrapper = await mountTab()

    expect(catalogApi.medicines.list).toHaveBeenCalledWith({ status: 'ACTIVE', size: 100 })
    const options = wrapper.findAll('#rx-20-medicine option').map((option) => option.text())
    expect(options).toEqual(['Chọn thuốc trong danh mục', 'Amoxicillin 500mg (Viên)', 'Vitamin C 500mg', 'Thuốc ngoài danh mục (tự nhập tên)'])
    expect(wrapper.find('#rx-20-name').exists()).toBe(false)

    await wrapper.find('#rx-20-quantity').setValue('14')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    expect(wrapper.find('#rx-20-medicine-error').text()).toContain('Phải chọn thuốc')
    expect(prescriptionItemsApi.create).not.toHaveBeenCalled()

    await wrapper.find('#rx-20-medicine').setValue('2')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    await flushPromises()
    expect(prescriptionItemsApi.create).toHaveBeenCalledWith(20, {
      medicineId: 2,
      quantity: 14,
      durationDays: undefined,
      dosage: undefined,
      frequency: undefined,
      instructions: undefined
    })
    expect(wrapper.find('[data-test="item-33"]').text()).toContain('Amoxicillin 500mg')
    expect(wrapper.find('#rx-20-medicine').element.value).toBe('')
    wrapper.unmount()
  })

  it('có danh mục thuốc: chọn "ngoài danh mục" thì hiện ô tên và gửi tên tự nhập', async () => {
    catalogApi.medicines.list.mockResolvedValue({ content: [{ id: 2, name: 'Amoxicillin 500mg', unit: 'Viên' }] })
    prescriptionItemsApi.create.mockResolvedValue({ id: 34, medicineName: 'Nước muối sinh lý', quantity: 2 })
    const wrapper = await mountTab()

    await wrapper.find('#rx-20-medicine').setValue('other')
    await wrapper.find('#rx-20-quantity').setValue('2')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    expect(wrapper.find('#rx-20-name-error').text()).toContain('Phải nhập tên thuốc')

    await wrapper.find('#rx-20-name').setValue('Nước muối sinh lý')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    await flushPromises()
    expect(prescriptionItemsApi.create.mock.calls[0][1]).toMatchObject({ medicineName: 'Nước muối sinh lý', quantity: 2 })
    expect(prescriptionItemsApi.create.mock.calls[0][1]).not.toHaveProperty('medicineId')
    wrapper.unmount()
  })

  it('không tải được danh mục thuốc: vẫn kê được đơn bằng ô nhập tên; chế độ chỉ đọc không gọi danh mục', async () => {
    catalogApi.medicines.list.mockRejectedValue(apiError(500, 'Lỗi hệ thống'))
    const wrapper = await mountTab()
    expect(wrapper.find('#rx-20-medicine').exists()).toBe(false)
    expect(wrapper.find('#rx-20-name').exists()).toBe(true)
    wrapper.unmount()

    catalogApi.medicines.list.mockClear()
    const readonly = await mountTab(false)
    expect(catalogApi.medicines.list).not.toHaveBeenCalled()
    readonly.unmount()
  })

  it('409 khi thêm thuốc: hiện lỗi và phát stale', async () => {
    prescriptionItemsApi.create.mockRejectedValue(apiError(409, 'Lần khám đã hoàn thành nên không thể kê hoặc sửa đơn thuốc'))
    const wrapper = await mountTab()
    await wrapper.find('#rx-20-name').setValue('Amoxicillin')
    await wrapper.find('#rx-20-quantity').setValue('3')
    await wrapper.find('[data-test="item-form-20"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-test="item-error"]').text()).toContain('đã hoàn thành')
    expect(wrapper.emitted('stale')).toHaveLength(1)
    wrapper.unmount()
  })

  it('tạo đơn thuốc mới và xóa một dòng thuốc có xác nhận', async () => {
    prescriptionsApi.createForEncounter.mockResolvedValue({ id: 21, prescriptionCode: 'RX-ZZ', status: 'ACTIVE' })
    prescriptionItemsApi.remove.mockResolvedValue()
    const wrapper = await mountTab()

    await wrapper.find('[data-test="create-prescription"]').trigger('click')
    await flushPromises()
    expect(prescriptionsApi.createForEncounter).toHaveBeenCalledWith(9, {})
    expect(wrapper.find('[data-test="prescription-21"]').exists()).toBe(true)

    await wrapper.find('[aria-label="Xóa Paracetamol 500mg"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Xóa thuốc khỏi đơn?')
    expect(prescriptionItemsApi.remove).not.toHaveBeenCalled()
    byText(document.body, 'Xóa thuốc').click()
    await flushPromises()

    expect(prescriptionItemsApi.remove).toHaveBeenCalledWith(31)
    expect(wrapper.find('[data-test="item-31"]').exists()).toBe(false)
    wrapper.unmount()
  })
})

describe('EncounterAttachmentsTab', () => {
  const FILES = [{ id: 7, originalFileName: 'xet-nghiem.pdf', fileSize: 204800, uploadedAt: '2026-10-02T10:00:00' }]

  const mountTab = async (editable = true) => {
    attachmentsApi.listByEncounter.mockResolvedValue(FILES)
    const wrapper = mount(EncounterAttachmentsTab, { props: { encounterId: 9, editable }, attachTo: document.body })
    await flushPromises()
    return wrapper
  }

  it('liệt kê tệp với kích thước đọc được; chế độ chỉ đọc không có tải lên/xóa', async () => {
    const readonly = await mountTab(false)
    expect(readonly.find('[data-test="attachment-7"]').text()).toContain('xet-nghiem.pdf')
    expect(readonly.find('[data-test="attachment-7"]').text()).toContain('200.0 KB')
    expect(readonly.find('[data-test="upload-form"]').exists()).toBe(false)
    expect(readonly.find('[aria-label="Xóa xet-nghiem.pdf"]').exists()).toBe(false)
    readonly.unmount()

    const editable = await mountTab(true)
    expect(editable.find('[data-test="upload-form"]').exists()).toBe(true)
    expect(editable.find('[aria-label="Xóa xet-nghiem.pdf"]').exists()).toBe(true)
    editable.unmount()
  })

  it('tải xuống gọi API dạng blob và lưu theo tên gốc', async () => {
    const blob = new Blob(['%PDF'])
    attachmentsApi.download.mockResolvedValue(blob)
    const wrapper = await mountTab()
    await wrapper.find('[data-test="attachment-7"] button').trigger('click')
    await flushPromises()

    expect(attachmentsApi.download).toHaveBeenCalledWith(7)
    expect(hoisted.saveBlob).toHaveBeenCalledWith(blob, 'xet-nghiem.pdf')
    wrapper.unmount()
  })

  it('tải lên: nút bị khóa khi chưa chọn tệp; 413 hiện thông báo quá lớn', async () => {
    const wrapper = await mountTab()
    const submit = () => wrapper.find('[data-test="upload-form"] button[type="submit"]')
    expect(submit().attributes('disabled')).toBeDefined()

    const file = new File(['abc'], 'anh.png', { type: 'image/png' })
    const input = wrapper.find('#encounter-file')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    expect(submit().attributes('disabled')).toBeUndefined()

    attachmentsApi.upload.mockRejectedValue(apiError(413, 'Quá lớn'))
    await wrapper.find('[data-test="upload-form"]').trigger('submit')
    await flushPromises()
    expect(attachmentsApi.upload).toHaveBeenCalledWith(9, file)
    expect(wrapper.text()).toContain('vượt quá giới hạn 10 MB')
    wrapper.unmount()
  })

  it('tải lên thành công thêm tệp vào danh sách', async () => {
    attachmentsApi.upload.mockResolvedValue({ id: 8, originalFileName: 'anh.png', fileSize: 3, uploadedAt: '2026-10-02T10:30:00' })
    const wrapper = await mountTab()
    const file = new File(['abc'], 'anh.png', { type: 'image/png' })
    const input = wrapper.find('#encounter-file')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await wrapper.find('[data-test="upload-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-test="attachment-8"]').text()).toContain('anh.png')
    expect(hoisted.toastSuccess).toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('EncounterInvoiceTab', () => {
  it('chưa có hóa đơn: hiện biểu mẫu, tính tạm tính và gửi dòng dịch vụ (không gửi đơn giá)', async () => {
    invoicesApi.createForEncounter.mockResolvedValue({ id: 5 })
    const wrapper = mount(EncounterInvoiceTab, { props: { encounterId: 9 }, attachTo: document.body })
    await flushPromises()

    expect(catalogApi.services.list).toHaveBeenCalled()
    await wrapper.find('#inv-service-0').setValue('1')
    await wrapper.find('#inv-quantity-0').setValue('2')
    await wrapper.find('#inv-discount').setValue('50000')
    const preview = wrapper.find('[data-test="invoice-preview"]').text()
    expect(preview.replace(/\s| /g, '')).toContain('400.000')
    expect(preview.replace(/\s| /g, '')).toContain('350.000')

    invoicesApi.byEncounter.mockResolvedValue({ id: 5, invoiceCode: 'INV-1', status: 'UNPAID', subtotal: 400000, discountAmount: 50000, totalAmount: 350000 })
    invoicesApi.items.mockResolvedValue([{ id: 1, description: 'Khám tổng quát', quantity: 2, unitPrice: 200000, lineTotal: 400000 }])
    await wrapper.find('[data-test="invoice-form"]').trigger('submit')
    await flushPromises()

    expect(invoicesApi.createForEncounter).toHaveBeenCalledWith(9, [{ serviceId: 1, quantity: 2 }], { discountAmount: 50000 })
    expect(wrapper.find('[data-test="invoice-view"]').text()).toContain('INV-1')
    expect(wrapper.find('[data-test="invoice-form"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('kiểm tra: phải chọn dịch vụ, giảm giá không vượt tạm tính', async () => {
    const wrapper = mount(EncounterInvoiceTab, { props: { encounterId: 9 }, attachTo: document.body })
    await flushPromises()

    await wrapper.find('[data-test="invoice-form"]').trigger('submit')
    await flushPromises()
    expect(invoicesApi.createForEncounter).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Phải chọn dịch vụ')

    await wrapper.find('#inv-service-0').setValue('1')
    await wrapper.find('#inv-discount').setValue('999999')
    await wrapper.find('[data-test="invoice-form"]').trigger('submit')
    await flushPromises()
    expect(invoicesApi.createForEncounter).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Giảm giá không được lớn hơn tạm tính')
    wrapper.unmount()
  })

  it('đã có hóa đơn: hiện chi tiết và không có biểu mẫu lập mới', async () => {
    invoicesApi.byEncounter.mockResolvedValue({ id: 5, invoiceCode: 'INV-7', status: 'PAID', subtotal: 200000, discountAmount: 0, totalAmount: 200000, issuedAt: '2026-10-02T10:00:00' })
    invoicesApi.items.mockResolvedValue([{ id: 1, description: 'Khám tổng quát', quantity: 1, unitPrice: 200000, lineTotal: 200000 }])
    const wrapper = mount(EncounterInvoiceTab, { props: { encounterId: 9 }, attachTo: document.body })
    await flushPromises()

    expect(wrapper.find('[data-test="invoice-view"]').text()).toContain('Khám tổng quát')
    expect(wrapper.find('[data-test="invoice-view"]').text()).toContain('Đã thu')
    expect(wrapper.find('[data-test="invoice-form"]').exists()).toBe(false)
    expect(catalogApi.services.list).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('lập hóa đơn lần hai (409) hiện lỗi rồi tải lại để thấy hóa đơn đã có', async () => {
    invoicesApi.createForEncounter.mockRejectedValue(apiError(409, 'Lần khám này đã có hóa đơn'))
    const wrapper = mount(EncounterInvoiceTab, { props: { encounterId: 9 }, attachTo: document.body })
    await flushPromises()
    await wrapper.find('#inv-service-0').setValue('2')

    invoicesApi.byEncounter.mockResolvedValue({ id: 6, invoiceCode: 'INV-9', status: 'UNPAID', subtotal: 250000, discountAmount: 0, totalAmount: 250000 })
    invoicesApi.items.mockResolvedValue([])
    await wrapper.find('[data-test="invoice-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-test="invoice-view"]').text()).toContain('INV-9')
    wrapper.unmount()
  })
})
