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
  specialtiesList: vi.fn(),
  servicesList: vi.fn(),
  medicinesList: vi.fn(),
  auditList: vi.fn(),
  doctorsListAdmin: vi.fn(),
  doctorsCreateAdmin: vi.fn(),
  doctorsUpdateAdmin: vi.fn(),
  doctorsRemoveAdmin: vi.fn(),
  appointmentsListAdmin: vi.fn(),
  invoicesListAdmin: vi.fn(),
  invoicesGet: vi.fn(),
  invoicesCollect: vi.fn(),
  invoicesVoid: vi.fn(),
  reportAppointments: vi.fn(),
  reportRevenue: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn()
}))

vi.mock('@/api/adminUsers', () => ({
  adminUsersApi: {
    list: mocks.usersList,
    create: mocks.usersCreate
  }
}))

vi.mock('@/api/catalog', () => ({
  catalogApi: {
    specialties: { list: mocks.specialtiesList },
    services: { list: mocks.servicesList },
    medicines: { list: mocks.medicinesList }
  }
}))

vi.mock('@/api/auditLogs', () => ({
  auditLogsApi: { list: mocks.auditList }
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
    get: mocks.invoicesGet,
    collect: mocks.invoicesCollect,
    void: mocks.invoicesVoid
  }
}))

vi.mock('@/api/adminReports', () => ({
  adminReportsApi: {
    appointments: mocks.reportAppointments,
    revenue: mocks.reportRevenue
  }
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError })
}))

function resetMocks() {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.usersList.mockResolvedValue({
    content: [{ id: 1, fullName: 'Admin Một', username: 'admin1', email: 'admin1@example.com', roles: ['ADMIN'], status: 'ACTIVE' }]
  })
  mocks.usersCreate.mockResolvedValue({})
  mocks.specialtiesList.mockResolvedValue({ content: [{ id: 1, code: 'NOI', name: 'Nội tổng quát', status: 'ACTIVE' }] })
  mocks.servicesList.mockResolvedValue({ content: [{ id: 1, code: 'K01', name: 'Khám tổng quát', price: 150000, status: 'ACTIVE' }] })
  mocks.medicinesList.mockResolvedValue({ content: [{ id: 1, code: 'M01', name: 'Paracetamol', unit: 'viên', status: 'ACTIVE' }] })
  mocks.auditList.mockResolvedValue({
    content: [{ id: 1, createdAt: '2026-10-01T00:00:00Z', actorUserId: 1, actionCode: 'VIEW_MEDICAL_RECORD', entityType: 'MedicalRecord', entityId: 1 }]
  })
  mocks.doctorsListAdmin.mockResolvedValue({
    content: [{ id: 1, fullName: 'Bác sĩ Lan', licenseNumber: 'CCHN-001', specialtyName: 'Nội tổng quát', isActive: true }]
  })
  mocks.appointmentsListAdmin.mockResolvedValue({
    content: [{ id: 1, patientName: 'Nguyễn Minh An', doctorName: 'Bác sĩ Lan', status: 'BOOKED', slot: { slotDate: '2026-10-02', startTime: '08:00', endTime: '08:30' } }],
    page: 0,
    totalPages: 1
  })
  mocks.invoicesListAdmin.mockResolvedValue({
    content: [{ id: 1, invoiceCode: 'INV-001', patientName: 'Nguyễn Minh An', issuedAt: '2026-10-01', totalAmount: 150000, status: 'UNPAID' }],
    page: 0,
    totalPages: 1
  })
  mocks.invoicesGet.mockResolvedValue({ id: 1, invoiceCode: 'INV-001', totalAmount: 150000, status: 'UNPAID', items: [] })
  mocks.reportAppointments.mockResolvedValue({ totalAppointments: 12, bookedCount: 5, completedCount: 6, cancelledCount: 1 })
  mocks.reportRevenue.mockResolvedValue({ invoicedAmount: 1000000, collectedAmount: 700000, unpaidAmount: 300000, voidCount: 1 })
}

describe('admin page tests', () => {
  it('renders admin dashboard quick links', () => {
    resetMocks()
    const wrapper = mount(AdminDashboardView)
    expect(wrapper.text()).toContain('Tổng quan quản trị')
    expect(wrapper.text()).toContain('Quản lý tài khoản')
    expect(wrapper.text()).toContain('Xem báo cáo')
  })

  it('renders users table and creates a user payload', async () => {
    resetMocks()
    const wrapper = mount(AdminUsersView)
    await flushPromises()
    expect(wrapper.text()).toContain('Admin Một')

    await wrapper.findAll('button').find((button) => button.text() === 'Tạo tài khoản').trigger('click')
    await flushPromises()
    document.querySelector('#create-full-name').value = 'Người dùng mới'
    document.querySelector('#create-full-name').dispatchEvent(new Event('input'))
    document.querySelector('#create-email').value = 'new@example.com'
    document.querySelector('#create-email').dispatchEvent(new Event('input'))
    document.querySelector('#create-username').value = 'newuser'
    document.querySelector('#create-username').dispatchEvent(new Event('input'))
    document.querySelector('#create-password').value = 'MedBook@2026'
    document.querySelector('#create-password').dispatchEvent(new Event('input'))
    Array.from(document.querySelectorAll('button')).find((button) => button.textContent.trim() === 'Tạo').click()
    await flushPromises()

    expect(mocks.usersCreate).toHaveBeenCalledWith(expect.objectContaining({ username: 'newuser', roles: ['PATIENT'] }))
  })

  it('renders catalog table with mocked specialties', async () => {
    resetMocks()
    const wrapper = mount(AdminCatalogView)
    await flushPromises()
    expect(wrapper.text()).toContain('Nội tổng quát')
  })

  it('renders audit logs table with mocked events', async () => {
    resetMocks()
    const wrapper = mount(AdminAuditLogsView)
    await flushPromises()
    expect(wrapper.text()).toContain('VIEW_MEDICAL_RECORD')
  })

  it('renders doctors table', async () => {
    resetMocks()
    const wrapper = mount(AdminDoctorsView)
    await flushPromises()
    expect(wrapper.text()).toContain('Bác sĩ Lan')
    expect(wrapper.text()).toContain('CCHN-001')
  })

  it('renders appointments table with administrative data only', async () => {
    resetMocks()
    const wrapper = mount(AdminAppointmentsView)
    await flushPromises()
    expect(wrapper.text()).toContain('Nguyễn Minh An')
    expect(wrapper.text()).toContain('Bác sĩ Lan')
  })

  it('renders invoices table and opens invoice details', async () => {
    resetMocks()
    const wrapper = mount(AdminInvoicesView)
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === 'INV-001').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('INV-001')
    expect(mocks.invoicesGet).toHaveBeenCalledWith(1)
  })

  it('renders reports from appointment and revenue endpoints', async () => {
    resetMocks()
    const wrapper = mount(AdminReportsView)
    await flushPromises()
    expect(wrapper.text()).toContain('Tổng lịch hẹn')
    expect(wrapper.text()).toContain('12')
    expect(mocks.reportAppointments).toHaveBeenCalled()
    expect(mocks.reportRevenue).toHaveBeenCalled()
  })
})
