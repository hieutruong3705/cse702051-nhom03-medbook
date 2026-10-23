export default [
  {
    path: 'dashboard',
    name: 'admin-dashboard',
    component: () => import('@/views/admin/AdminDashboardView.vue'),
    meta: { roles: ['ADMIN'], title: 'Tổng quan quản trị' }
  },
  {
    path: 'users',
    name: 'admin-users',
    component: () => import('@/views/admin/AdminUsersView.vue'),
    meta: { roles: ['ADMIN'], title: 'Tài khoản' }
  },
  {
    path: 'doctors',
    name: 'admin-doctors',
    component: () => import('@/views/admin/AdminDoctorsView.vue'),
    meta: { roles: ['ADMIN'], title: 'Bác sĩ' }
  },
  {
    path: 'catalog',
    name: 'admin-catalog',
    component: () => import('@/views/admin/AdminCatalogView.vue'),
    meta: { roles: ['ADMIN'], title: 'Danh mục' }
  },
  {
    path: 'appointments',
    name: 'admin-appointments',
    component: () => import('@/views/admin/AdminAppointmentsView.vue'),
    meta: { roles: ['ADMIN'], title: 'Lịch hẹn' }
  },
  {
    path: 'invoices',
    name: 'admin-invoices',
    component: () => import('@/views/admin/AdminInvoicesView.vue'),
    meta: { roles: ['ADMIN'], title: 'Hóa đơn' }
  },
  {
    path: 'reports',
    name: 'admin-reports',
    component: () => import('@/views/admin/AdminReportsView.vue'),
    meta: { roles: ['ADMIN'], title: 'Báo cáo' }
  },
  {
    path: 'audit-logs',
    name: 'admin-audit-logs',
    component: () => import('@/views/admin/AdminAuditLogsView.vue'),
    meta: { roles: ['ADMIN'], title: 'Nhật ký hệ thống' }
  }
]
