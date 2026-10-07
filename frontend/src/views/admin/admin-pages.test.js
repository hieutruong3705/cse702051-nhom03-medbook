import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AdminAppointmentsView from './AdminAppointmentsView.vue'
import AdminAuditLogsView from './AdminAuditLogsView.vue'
import AdminCatalogView from './AdminCatalogView.vue'
import AdminDashboardView from './AdminDashboardView.vue'
import AdminDoctorsView from './AdminDoctorsView.vue'
import AdminInvoicesView from './AdminInvoicesView.vue'
import AdminReportsView from './AdminReportsView.vue'
import AdminUsersView from './AdminUsersView.vue'

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
  }
})

const mocks = vi.hoisted(() => ({
  usersList: vi.fn(),
  usersCreate: vi.fn(),
  usersChangeStatus: vi.fn(),
  usersChangeRoles: vi.fn(),
  specialtiesList: vi.fn(),
  specialtiesListAdmin: vi.fn(),
  specialtiesCreate: vi.fn(),
  specialtiesUpdate: vi.fn(),
  specialtiesRemove: vi.fn(),
  servicesListAdmin: vi.fn(),
  servicesCreate: vi.fn(),
  medicinesListAdmin: vi.fn(),
  auditList: vi.fn(),
  auditActionCodes: vi.fn(),
  doctorsListAdmin: vi.fn(),
  doctorsCreateAdmin: vi.fn(),
  doctorsUpdateAdmin: vi.fn(),
  doctorsRemoveAdmin: vi.fn(),
  appointmentsListAdmin: vi.fn(),
  invoicesListAdmin: vi.fn(),
  invoicesItems: vi.fn(),
  invoicesCollect: vi.fn(),
  invoicesVoid: vi.fn(),
  reportAppointments: vi.fn(),
  reportRevenue: vi.fn(),
  reportServices: vi.fn(),
  exportAppointments: vi.fn(),
  exportRevenue: vi.fn(),
  exportServices: vi.fn(),
  downloadBlob: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
  toastInfo: vi.fn()
}))

vi.mock('@/api/adminUsers', () => ({
  adminUsersApi: {
    list: mocks.usersList,
    create: mocks.usersCreate,
    changeStatus: mocks.usersChangeStatus,
    changeRoles: mocks.usersChangeRoles
  }
}))

vi.mock('@/api/catalog', () => ({
  catalogApi: {
    specialties: {
      list: mocks.specialtiesList,
      listAdmin: mocks.specialtiesListAdmin,
      create: mocks.specialtiesCreate,
      update: mocks.specialtiesUpdate,
      remove: mocks.specialtiesRemove
    },
    services: { listAdmin: mocks.servicesListAdmin, create: mocks.servicesCreate },
    medicines: { listAdmin: mocks.medicinesListAdmin }
  }
}))

vi.mock('@/api/auditLogs', () => ({
  auditLogsApi: { list: mocks.auditList, actionCodes: mocks.auditActionCodes }
}))

vi.mock('@/api/doctors', () => ({
  doctorsApi: {
    listAdmin: mocks.doctorsListAdmin,
    createAdmin: mocks.doctorsCreateAdmin,
    updateAdmin: mocks.doctorsUpdateAdmin,
    removeAdmin: mocks.doctorsRemoveAdmin
  }
}))

vi.mock('@/api/appointments', () => ({
  appointmentsApi: { listAdmin: mocks.appointmentsListAdmin }
}))

vi.mock('@/api/invoices', () => ({
  invoicesApi: {
    listAdmin: mocks.invoicesListAdmin,
    items: mocks.invoicesItems,
    collect: mocks.invoicesCollect,
    void: mocks.invoicesVoid
  }
}))

vi.mock('@/api/adminReports', () => ({
  adminReportsApi: {
    appointments: mocks.reportAppointments,
    revenue: mocks.reportRevenue,
    services: mocks.reportServices,
    exportAppointments: mocks.exportAppointments,
    exportRevenue: mocks.exportRevenue,
    exportServices: mocks.exportServices
  }
}))

vi.mock('@/utils/params', async (importOriginal) => ({
  ...(await importOriginal()),
  downloadBlob: mocks.downloadBlob
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError, info: mocks.toastInfo })
}))

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })
const apiError = (status, message, details = {}) => Object.assign(new Error(message), { status, details })

// Dữ liệu mẫu dùng đúng tên trường của DTO phía máy chủ (AdminUserDTO, DoctorAdminDTO, AdminInvoiceDTO...).
function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  document.body.innerHTML = ''
  mocks.usersList.mockResolvedValue(page([
    { id: 1, username: 'admin1', email: 'admin1@example.com', fullName: 'Admin Một', phone: null, status: 'ACTIVE', roles: ['ADMIN'], patientId: null, doctorId: null },
    { id: 9, username: 'bs.moi', email: 'bsmoi@example.com', fullName: 'Bác sĩ Mới', phone: '0911222333', status: 'ACTIVE', roles: ['DOCTOR'], patientId: null, doctorId: null },
    { id: 8, username: 'bn.khoa', email: 'khoa@example.com', fullName: 'Bệnh nhân Bị Khóa', phone: null, status: 'LOCKED', roles: ['PATIENT'], patientId: 3, doctorId: null }
  ]))
  mocks.usersCreate.mockResolvedValue({})
  mocks.usersChangeStatus.mockResolvedValue({})
  mocks.usersChangeRoles.mockResolvedValue({})
  const specialties = page([{ id: 1, code: 'NOI', name: 'Nội tổng quát', description: null, status: 'ACTIVE' }])
  mocks.specialtiesList.mockResolvedValue(specialties)
  mocks.specialtiesListAdmin.mockResolvedValue(specialties)
  mocks.specialtiesCreate.mockResolvedValue({})
  mocks.specialtiesUpdate.mockResolvedValue({})
  mocks.specialtiesRemove.mockResolvedValue(null)
  mocks.servicesListAdmin.mockResolvedValue(page([{ id: 1, code: 'K01', name: 'Khám tổng quát', durationMinutes: 30, price: 150000, status: 'ACTIVE' }]))
  mocks.servicesCreate.mockResolvedValue({})
  mocks.medicinesListAdmin.mockResolvedValue(page([{ id: 1, code: 'M01', name: 'Paracetamol', unit: 'Viên', status: 'INACTIVE' }]))
  mocks.auditList.mockResolvedValue(page([
    { id: 1, createdAt: '2026-10-01T00:00:00', actorUserId: 1, actionCode: 'MEDICAL_RECORD_VIEW', entityType: 'medical_records', entityId: 1, ipAddress: '127.0.0.1', metadataJson: '{"patientId":4}' }
  ]))
  mocks.auditActionCodes.mockResolvedValue(['LOGIN_FAILED', 'MEDICAL_RECORD_VIEW'])
  mocks.doctorsListAdmin.mockResolvedValue(page([
    { id: 1, userId: 2, username: 'doctor1', fullName: 'Bác sĩ Lan', specialtyId: 1, specialtyName: 'Nội tổng quát', phone: '0912345678', licenseNumber: 'CCHN-001', bio: null, isActive: true, hasHistory: true }
  ]))
  mocks.doctorsCreateAdmin.mockResolvedValue({})
  mocks.doctorsUpdateAdmin.mockResolvedValue({})
  mocks.doctorsRemoveAdmin.mockResolvedValue(null)
  mocks.appointmentsListAdmin.mockResolvedValue(page([
    { id: 1, patientName: 'Nguyễn Minh An', patientCode: 'BN000001', doctorName: 'Bác sĩ Lan', specialtyName: 'Nội tổng quát', serviceName: 'Khám tổng quát', date: '2026-10-02', startTime: '08:00:00', endTime: '08:30:00', status: 'BOOKED' }
  ]))
  mocks.invoicesListAdmin.mockResolvedValue(page([
    { id: 1, invoiceCode: 'INV-001', patientId: 4, patientName: 'Nguyễn Minh An', patientCode: 'BN000001', subtotal: 150000, discountAmount: 0, totalAmount: 150000, status: 'UNPAID', issuedAt: '2026-10-01T09:00:00' },
    { id: 2, invoiceCode: 'INV-002', patientId: 5, patientName: 'Trần Gia Bình', patientCode: 'BN000002', subtotal: 300000, discountAmount: 0, totalAmount: 300000, status: 'PAID', issuedAt: '2026-10-01T10:00:00', paidAt: '2026-10-01T10:05:00' }
  ]))
  mocks.invoicesItems.mockResolvedValue([{ id: 1, serviceId: 1, description: 'Khám tổng quát', quantity: 1, unitPrice: 150000, lineTotal: 150000 }])
  mocks.invoicesCollect.mockResolvedValue({})
  mocks.invoicesVoid.mockResolvedValue({})
  mocks.reportAppointments.mockResolvedValue({
    from: '2026-09-08', to: '2026-10-07', groupBy: 'DAY', total: 12, booked: 4, inProgress: 1, completed: 6, cancelled: 1, cancellationRate: 8.33,
    groups: [{ key: '2026-10-01', label: '01/10/2026', total: 12, completed: 6, cancelled: 1, cancellationRate: 8.33 }]
  })
  mocks.reportRevenue.mockResolvedValue({
    from: '2026-09-08', to: '2026-10-07', groupBy: 'DAY', invoicedAmount: 1000000, collectedAmount: 700000, unpaidAmount: 300000, invoiceCount: 5, voidCount: 1,
    groups: [{ key: '2026-10-01', label: '01/10/2026', invoicedAmount: 1000000, collectedAmount: 700000, unpaidAmount: 300000, invoiceCount: 5, voidCount: 1 }]
  })
  mocks.reportServices.mockResolvedValue({
    from: '2026-09-08', to: '2026-10-07', totalQuantity: 4, totalAmount: 550000,
    services: [
      { serviceId: 1, serviceCode: 'DV04', serviceName: 'Siêu âm', invoiceCount: 2, quantity: 3, amount: 300000, sharePercent: 54.55 },
      { serviceId: 2, serviceCode: 'DV03', serviceName: 'Xét nghiệm máu', invoiceCount: 1, quantity: 1, amount: 250000, sharePercent: 45.45 }
    ]
  })
  mocks.exportAppointments.mockResolvedValue(new Blob(['a']))
  mocks.exportRevenue.mockResolvedValue(new Blob(['b']))
  mocks.exportServices.mockResolvedValue(new Blob(['c']))
}

const clickButton = async (wrapper, text) => {
  const button = wrapper.findAll('button').find((item) => item.text() === text)
  expect(button, `nút "${text}"`).toBeTruthy()
  await button.trigger('click')
  await flushPromises()
}
// Bấm nút trong hộp thoại đang mở (không lẫn với nút cùng tên trong bảng phía sau).
const clickInBody = async (text) => {
  const dialogs = document.querySelectorAll('[role="dialog"]')
  const scope = dialogs[dialogs.length - 1] || document
  const button = Array.from(scope.querySelectorAll('button')).find((item) => item.textContent.trim() === text)
  expect(button, `nút "${text}" trong hộp thoại`).toBeTruthy()
  button.click()
  await flushPromises()
}
const typeInBody = (selector, value, event = 'input') => {
  const element = document.querySelector(selector)
  expect(element, selector).toBeTruthy()
  element.value = value
  element.dispatchEvent(new Event(event))
}

describe('trang quản trị', () => {
  it('trang tổng quan có các lối tắt', () => {
    resetMocks()
    const wrapper = mount(AdminDashboardView)
    expect(wrapper.text()).toContain('Tổng quan quản trị')
    expect(wrapper.text()).toContain('Quản lý tài khoản')
    expect(wrapper.text()).toContain('Xem báo cáo')
  })

  describe('tài khoản', () => {
    it('hiện danh sách với nhãn vai trò, trạng thái tiếng Việt và không gửi bộ lọc rỗng', async () => {
      resetMocks()
      const wrapper = mount(AdminUsersView, { attachTo: document.body })
      await flushPromises()
      expect(wrapper.text()).toContain('Admin Một')
      expect(wrapper.text()).toContain('Quản trị viên')
      expect(wrapper.text()).toContain('Đã khóa')
      expect(mocks.usersList).toHaveBeenCalledWith({ page: 0, size: 20 })
      expect(wrapper.find('#admin-user-status').text()).not.toContain('Ngừng dùng')
      wrapper.unmount()
    })

    it('tạo tài khoản bệnh nhân: gửi role (một giá trị), không gửi doctorProfile', async () => {
      resetMocks()
      const wrapper = mount(AdminUsersView, { attachTo: document.body })
      await flushPromises()
      await clickButton(wrapper, 'Tạo tài khoản')
      typeInBody('#create-full-name', 'Người dùng mới')
      typeInBody('#create-email', 'new@example.com')
      typeInBody('#create-username', 'newuser')
      typeInBody('#create-password', 'MedBook@2026')
      await clickInBody('Tạo')

      expect(mocks.usersCreate).toHaveBeenCalledWith({
        username: 'newuser', password: 'MedBook@2026', fullName: 'Người dùng mới', email: 'new@example.com', phone: '', role: 'PATIENT'
      })
      expect(mocks.toastSuccess).toHaveBeenCalledWith('Đã tạo tài khoản')
      wrapper.unmount()
    })

    it('tạo tài khoản bác sĩ: bắt buộc kèm hồ sơ bác sĩ; lỗi 409 hiện đúng ô bị trùng', async () => {
      resetMocks()
      mocks.usersCreate.mockRejectedValueOnce(apiError(409, 'Tên đăng nhập đã được sử dụng', { username: 'Tên đăng nhập đã được sử dụng' }))
      const wrapper = mount(AdminUsersView, { attachTo: document.body })
      await flushPromises()
      await clickButton(wrapper, 'Tạo tài khoản')
      typeInBody('#create-full-name', 'Bác sĩ Hòa')
      typeInBody('#create-email', 'hoa@example.com')
      typeInBody('#create-username', 'bs.hoa')
      typeInBody('#create-password', 'MedBook@2026')
      typeInBody('#create-roles', 'DOCTOR', 'change')
      await flushPromises()
      typeInBody('#create-doctor-specialty', '1', 'change')
      typeInBody('#create-doctor-license', 'CCHN-777')
      await clickInBody('Tạo')

      expect(mocks.usersCreate).toHaveBeenCalledWith(expect.objectContaining({
        role: 'DOCTOR', doctorProfile: { specialtyId: 1, licenseNumber: 'CCHN-777' }
      }))
      expect(document.querySelector('#create-username-error').textContent).toContain('đã được sử dụng')
      expect(mocks.toastSuccess).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('khóa tài khoản kèm lý do và mở khóa tài khoản đang bị khóa', async () => {
      resetMocks()
      const wrapper = mount(AdminUsersView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Khóa tài khoản bs.moi"]').trigger('click')
      await flushPromises()
      typeInBody('#user-status-reason', 'Vi phạm quy định')
      await clickInBody('Khóa tài khoản')
      expect(mocks.usersChangeStatus).toHaveBeenCalledWith(9, { status: 'LOCKED', reason: 'Vi phạm quy định' })

      await wrapper.find('button[aria-label="Mở khóa tài khoản bn.khoa"]').trigger('click')
      await flushPromises()
      await clickInBody('Mở khóa')
      expect(mocks.usersChangeStatus).toHaveBeenLastCalledWith(8, { status: 'ACTIVE', reason: '' })
      wrapper.unmount()
    })

    it('đổi vai trò; máy chủ từ chối (409) thì hiện đúng thông điệp và không đóng hộp thoại', async () => {
      resetMocks()
      mocks.usersChangeRoles.mockRejectedValueOnce(apiError(409, 'Không thể tự gỡ quyền ADMIN của chính mình!'))
      const wrapper = mount(AdminUsersView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Đổi vai trò của admin1"]').trigger('click')
      await flushPromises()
      const doctorBox = document.querySelector('#role-DOCTOR')
      doctorBox.checked = true
      doctorBox.dispatchEvent(new Event('change'))
      await clickInBody('Lưu vai trò')

      expect(mocks.usersChangeRoles).toHaveBeenCalledWith(1, { roles: ['ADMIN', 'DOCTOR'] })
      expect(document.body.textContent).toContain('Không thể tự gỡ quyền ADMIN của chính mình!')
      wrapper.unmount()
    })

    it('lỗi tải danh sách hiện thông báo trong bảng', async () => {
      resetMocks()
      mocks.usersList.mockRejectedValueOnce(apiError(500, 'x'))
      const wrapper = mount(AdminUsersView)
      await flushPromises()
      expect(wrapper.text()).toContain('Hệ thống đang bận')
    })
  })

  describe('danh mục', () => {
    it('dùng danh sách quản trị (gồm cả mục ngừng dùng) và đổi tab', async () => {
      resetMocks()
      const wrapper = mount(AdminCatalogView)
      await flushPromises()
      expect(wrapper.text()).toContain('Nội tổng quát')
      expect(mocks.specialtiesListAdmin).toHaveBeenCalledWith({ page: 0, size: 20 })
      expect(mocks.specialtiesList).not.toHaveBeenCalled()

      await clickButton(wrapper, 'Thuốc')
      expect(mocks.medicinesListAdmin).toHaveBeenCalled()
      expect(wrapper.text()).toContain('Paracetamol')
      expect(wrapper.text()).toContain('Ngừng dùng')
    })

    it('thêm dịch vụ: gửi đúng mã, tên, thời lượng, giá dạng số', async () => {
      resetMocks()
      const wrapper = mount(AdminCatalogView, { attachTo: document.body })
      await flushPromises()
      await clickButton(wrapper, 'Dịch vụ')
      await clickButton(wrapper, 'Thêm dịch vụ')
      typeInBody('#catalog-code', 'DV09')
      typeInBody('#catalog-name', 'Điện tim')
      typeInBody('#catalog-duration', '20')
      typeInBody('#catalog-price', '180000')
      await clickInBody('Lưu')

      expect(mocks.servicesCreate).toHaveBeenCalledWith({
        code: 'DV09', name: 'Điện tim', description: '', status: 'ACTIVE', durationMinutes: 20, price: 180000
      })
      wrapper.unmount()
    })

    it('sửa chuyên khoa không cho đổi mã; lỗi kiểm tra dữ liệu hiện ở đúng ô', async () => {
      resetMocks()
      mocks.specialtiesUpdate.mockRejectedValueOnce(apiError(400, 'Dữ liệu đầu vào không hợp lệ', { name: 'Tên không được để trống' }))
      const wrapper = mount(AdminCatalogView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Sửa Nội tổng quát"]').trigger('click')
      await flushPromises()
      expect(document.querySelector('#catalog-code').disabled).toBe(true)
      typeInBody('#catalog-name', ' ')
      await clickInBody('Lưu')

      expect(mocks.specialtiesUpdate).toHaveBeenCalledWith(1, expect.objectContaining({ code: 'NOI', name: '' }))
      expect(document.querySelector('#catalog-name-error').textContent).toContain('Tên không được để trống')
      wrapper.unmount()
    })

    it('xóa mục đang được tham chiếu: báo rõ chỉ ngừng dùng, không phải xóa hẳn', async () => {
      resetMocks()
      mocks.specialtiesRemove.mockResolvedValueOnce({ id: 1, code: 'NOI', name: 'Nội tổng quát', status: 'INACTIVE' })
      const wrapper = mount(AdminCatalogView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Xóa Nội tổng quát"]').trigger('click')
      await flushPromises()
      await clickInBody('Xóa')

      expect(mocks.specialtiesRemove).toHaveBeenCalledWith(1)
      expect(mocks.toastInfo).toHaveBeenCalledWith(expect.stringContaining('đang được tham chiếu'), 'Đã ngừng dùng')
      expect(mocks.toastSuccess).not.toHaveBeenCalled()
      wrapper.unmount()
    })
  })

  describe('nhật ký hệ thống', () => {
    it('hiện sự kiện, nạp danh sách mã hành động, không hiện metadata', async () => {
      resetMocks()
      const wrapper = mount(AdminAuditLogsView)
      await flushPromises()
      expect(wrapper.text()).toContain('MEDICAL_RECORD_VIEW')
      expect(wrapper.text()).toContain('medical_records #1')
      expect(wrapper.text()).not.toContain('patientId')
      expect(wrapper.find('#audit-action').text()).toContain('LOGIN_FAILED')

      await wrapper.find('#audit-action').setValue('LOGIN_FAILED')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(mocks.auditList).toHaveBeenLastCalledWith({ actionCode: 'LOGIN_FAILED', page: 0, size: 20 })
    })
  })

  describe('bác sĩ', () => {
    it('hiện danh sách và lọc bằng isActive', async () => {
      resetMocks()
      const wrapper = mount(AdminDoctorsView)
      await flushPromises()
      expect(wrapper.text()).toContain('Bác sĩ Lan')
      expect(wrapper.text()).toContain('CCHN-001')
      expect(wrapper.text()).toContain('Nội tổng quát')

      await wrapper.find('#admin-doctor-status').setValue('false')
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(mocks.doctorsListAdmin).toHaveBeenLastCalledWith(expect.objectContaining({ isActive: 'false', page: 0 }))
      expect(mocks.doctorsListAdmin.mock.lastCall[0]).not.toHaveProperty('keyword')
    })

    it('thêm hồ sơ: chỉ chọn được tài khoản bác sĩ chưa có hồ sơ và gửi kèm userId', async () => {
      resetMocks()
      const wrapper = mount(AdminDoctorsView, { attachTo: document.body })
      await flushPromises()
      await clickButton(wrapper, 'Thêm hồ sơ bác sĩ')
      expect(mocks.usersList).toHaveBeenCalledWith({ role: 'DOCTOR', status: 'ACTIVE', size: 100 })
      const options = Array.from(document.querySelectorAll('#admin-doctor-user option')).map((option) => option.textContent.trim())
      expect(options).toContain('Bác sĩ Mới (bs.moi)')

      typeInBody('#admin-doctor-user', '9', 'change')
      typeInBody('#admin-doctor-name', 'BS. Mới')
      typeInBody('#admin-doctor-license', 'CCHN-900')
      typeInBody('#admin-doctor-specialty', '1', 'change')
      await clickInBody('Lưu')

      expect(mocks.doctorsCreateAdmin).toHaveBeenCalledWith({
        userId: 9, fullName: 'BS. Mới', licenseNumber: 'CCHN-900', phone: '', specialtyId: 1, bio: ''
      })
      wrapper.unmount()
    })

    it('xóa bác sĩ đã có lịch sử: báo rõ hồ sơ chỉ ngừng hoạt động', async () => {
      resetMocks()
      mocks.doctorsRemoveAdmin.mockResolvedValueOnce({ id: 1, fullName: 'Bác sĩ Lan', isActive: false })
      const wrapper = mount(AdminDoctorsView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Xóa hồ sơ Bác sĩ Lan"]').trigger('click')
      await flushPromises()
      await clickInBody('Xóa')

      expect(mocks.doctorsRemoveAdmin).toHaveBeenCalledWith(1)
      expect(mocks.toastInfo).toHaveBeenCalledWith(expect.stringContaining('ngừng hoạt động'), 'Đã ngừng hoạt động')
      wrapper.unmount()
    })
  })

  it('lịch hẹn: chỉ có thông tin hành chính, đọc ngày giờ từ date, startTime, endTime', async () => {
    resetMocks()
    const wrapper = mount(AdminAppointmentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(wrapper.text()).toContain('08:00 - 08:30')
    expect(mocks.appointmentsListAdmin).toHaveBeenCalledWith({ page: 0, size: 10 })
  })

  describe('hóa đơn', () => {
    it('mở chi tiết: nạp dòng hóa đơn và hiện cột mô tả dịch vụ', async () => {
      resetMocks()
      const wrapper = mount(AdminInvoicesView, { attachTo: document.body })
      await flushPromises()
      await clickButton(wrapper, 'INV-001')
      expect(mocks.invoicesItems).toHaveBeenCalledWith(1)
      expect(document.body.textContent).toContain('Khám tổng quát')
      expect(document.body.textContent).toContain('Tổng giá trị hóa đơn')
      wrapper.unmount()
    })

    it('chỉ hóa đơn chưa thu mới có nút thu và hủy; thu gọi PATCH collect', async () => {
      resetMocks()
      const wrapper = mount(AdminInvoicesView, { attachTo: document.body })
      await flushPromises()
      expect(wrapper.find('button[aria-label="Hủy hóa đơn INV-002"]').attributes('disabled')).toBeDefined()
      expect(wrapper.find('button[aria-label="Ghi nhận đã thu INV-002"]').attributes('disabled')).toBeDefined()

      await wrapper.find('button[aria-label="Ghi nhận đã thu INV-001"]').trigger('click')
      await flushPromises()
      await clickInBody('Đã thu')
      expect(mocks.invoicesCollect).toHaveBeenCalledWith(1, {})
      expect(mocks.invoicesListAdmin).toHaveBeenCalledTimes(2)
      wrapper.unmount()
    })

    it('hủy hóa đơn bắt buộc nhập lý do và gửi reason', async () => {
      resetMocks()
      const wrapper = mount(AdminInvoicesView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Hủy hóa đơn INV-001"]').trigger('click')
      await flushPromises()
      await clickInBody('Hủy hóa đơn')
      expect(mocks.invoicesVoid).not.toHaveBeenCalled()
      expect(document.querySelector('#void-reason-error').textContent).toContain('Phải nhập lý do hủy')

      typeInBody('#void-reason', 'Lập nhầm dịch vụ')
      await clickInBody('Hủy hóa đơn')
      expect(mocks.invoicesVoid).toHaveBeenCalledWith(1, { reason: 'Lập nhầm dịch vụ' })
      wrapper.unmount()
    })

    it('thu bị từ chối 409 (người khác vừa thu): hiện thông điệp của máy chủ và tải lại danh sách', async () => {
      resetMocks()
      mocks.invoicesCollect.mockRejectedValueOnce(apiError(409, 'Hóa đơn đã được thu trước đó!'))
      const wrapper = mount(AdminInvoicesView, { attachTo: document.body })
      await flushPromises()
      await wrapper.find('button[aria-label="Ghi nhận đã thu INV-001"]').trigger('click')
      await flushPromises()
      await clickInBody('Đã thu')
      expect(mocks.toastError).toHaveBeenCalledWith('Hóa đơn đã được thu trước đó!')
      expect(mocks.invoicesListAdmin).toHaveBeenCalledTimes(2)
      wrapper.unmount()
    })
  })

  describe('báo cáo', () => {
    it('hiện ba báo cáo với biểu đồ và bảng số liệu; gửi groupBy đúng hợp đồng', async () => {
      resetMocks()
      const wrapper = mount(AdminReportsView)
      await flushPromises()
      expect(mocks.reportAppointments).toHaveBeenCalledWith({ groupBy: 'DAY' })
      expect(mocks.reportRevenue).toHaveBeenCalledWith({ groupBy: 'DAY' })
      expect(mocks.reportServices).toHaveBeenCalledWith({})
      expect(wrapper.text()).toContain('Tổng lịch khám')
      expect(wrapper.text()).toContain('12')
      expect(wrapper.text()).toContain('Giá trị lập hóa đơn')
      expect(wrapper.text()).toContain('Siêu âm')
      // mỗi báo cáo có một biểu đồ tròn (tỷ trọng) và một biểu đồ cột hoặc thanh ngang kèm bảng số liệu
      expect(wrapper.findAll('[role="img"]')).toHaveLength(6)
      expect(wrapper.findAll('svg[role="img"]')).toHaveLength(3)
      expect(wrapper.text()).toContain('Tỷ lệ lịch khám theo trạng thái')
      expect(wrapper.text()).toContain('Tỷ lệ đã thu và chưa thu')
      expect(wrapper.text()).toContain('Tỷ trọng thành tiền theo dịch vụ')
      expect(wrapper.text()).toContain('70%')
      expect(wrapper.findAll('table')).toHaveLength(3)
      expect(wrapper.find('#report-appointment-group').text()).not.toContain('Tuần')

      await wrapper.find('#report-appointment-group').setValue('DOCTOR')
      await flushPromises()
      expect(mocks.reportAppointments).toHaveBeenLastCalledWith({ groupBy: 'DOCTOR' })
      await wrapper.find('#report-revenue-group').setValue('MONTH')
      await flushPromises()
      expect(mocks.reportRevenue).toHaveBeenLastCalledWith({ groupBy: 'MONTH' })
    })

    it('lọc nhanh theo khoảng thời gian và nhóm theo tháng, năm', async () => {
      resetMocks()
      const wrapper = mount(AdminReportsView)
      await flushPromises()
      const today = new Date()
      const iso = (date) =>
        `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

      await wrapper.find('#report-period').setValue('THIS_YEAR')
      await flushPromises()
      const year = { from: `${today.getFullYear()}-01-01`, to: iso(today) }
      expect(mocks.reportAppointments).toHaveBeenLastCalledWith({ ...year, groupBy: 'DAY' })
      expect(mocks.reportRevenue).toHaveBeenLastCalledWith({ ...year, groupBy: 'DAY' })
      expect(mocks.reportServices).toHaveBeenLastCalledWith(year)
      expect(wrapper.find('#report-from').element.value).toBe(year.from)

      await wrapper.find('#report-period').setValue('THIS_MONTH')
      await flushPromises()
      expect(mocks.reportServices).toHaveBeenLastCalledWith({ from: iso(new Date(today.getFullYear(), today.getMonth(), 1)), to: iso(today) })

      await wrapper.find('#report-appointment-group').setValue('YEAR')
      await wrapper.find('#report-revenue-group').setValue('YEAR')
      await flushPromises()
      expect(mocks.reportAppointments).toHaveBeenLastCalledWith(expect.objectContaining({ groupBy: 'YEAR' }))
      expect(mocks.reportRevenue).toHaveBeenLastCalledWith(expect.objectContaining({ groupBy: 'YEAR' }))

      // tự nhập ngày thì bộ chọn nhanh chuyển sang "Tùy chọn" và không tự tải lại
      const calls = mocks.reportServices.mock.calls.length
      await wrapper.find('#report-from').setValue('2026-01-15')
      await flushPromises()
      expect(wrapper.find('#report-period').element.value).toBe('CUSTOM')
      expect(mocks.reportServices.mock.calls.length).toBe(calls)

      await wrapper.find('#report-period').setValue('LAST_30')
      await flushPromises()
      expect(mocks.reportServices).toHaveBeenLastCalledWith({})
    })

    it('xuất CSV cho cả ba báo cáo theo khoảng ngày đang chọn', async () => {
      resetMocks()
      const wrapper = mount(AdminReportsView)
      await flushPromises()
      await wrapper.find('#report-from').setValue('2026-10-01')
      await wrapper.find('#report-to').setValue('2026-10-07')

      await clickButton(wrapper, 'Xuất CSV lịch khám')
      await clickButton(wrapper, 'Xuất CSV doanh thu')
      await clickButton(wrapper, 'Xuất CSV dịch vụ')

      const range = { from: '2026-10-01', to: '2026-10-07' }
      expect(mocks.exportAppointments).toHaveBeenCalledWith(range)
      expect(mocks.exportRevenue).toHaveBeenCalledWith({ ...range, groupBy: 'DAY' })
      expect(mocks.exportServices).toHaveBeenCalledWith(range)
      expect(mocks.downloadBlob).toHaveBeenCalledTimes(3)
      expect(mocks.downloadBlob).toHaveBeenLastCalledWith(expect.any(Blob), 'medbook-dich-vu-2026-10-01_2026-10-07.csv')
    })

    it('một báo cáo lỗi không làm hỏng hai báo cáo còn lại; báo cáo rỗng có thông báo', async () => {
      resetMocks()
      mocks.reportRevenue.mockRejectedValueOnce(apiError(400, 'Khoảng thời gian tối đa 366 ngày'))
      mocks.reportServices.mockResolvedValueOnce({ totalQuantity: 0, totalAmount: 0, services: [] })
      const wrapper = mount(AdminReportsView)
      await flushPromises()
      expect(wrapper.text()).toContain('Khoảng thời gian tối đa 366 ngày')
      expect(wrapper.text()).toContain('Tổng lịch khám')
      expect(wrapper.text()).toContain('Không có dữ liệu trong khoảng thời gian này.')
    })
  })
})
