// Route con của khu lễ tân (/receptionist).
export default [
  {
    path: 'dashboard',
    name: 'receptionist-dashboard',
    component: () => import('@/views/receptionist/ReceptionistOverviewView.vue'),
    meta: { title: 'Tổng quan lễ tân' }
  },
  {
    path: 'patients',
    name: 'receptionist-patients',
    component: () => import('@/views/receptionist/ReceptionistPatientsView.vue'),
    meta: { title: 'Bệnh nhân' }
  },
  {
    path: 'appointments',
    name: 'receptionist-appointments',
    component: () => import('@/views/receptionist/ReceptionistAppointmentsView.vue'),
    meta: { title: 'Lịch hẹn' }
  },
  {
    path: 'booking',
    name: 'receptionist-booking',
    component: () => import('@/views/receptionist/ReceptionistBookingView.vue'),
    meta: { title: 'Đặt lịch hộ' }
  },
  {
    path: 'invoices',
    name: 'receptionist-invoices',
    component: () => import('@/views/receptionist/ReceptionistInvoicesView.vue'),
    meta: { title: 'Hóa đơn' }
  }
]
