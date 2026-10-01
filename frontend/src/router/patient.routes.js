// Child routes under /patient.
export default [
  {
    path: 'dashboard',
    name: 'patient-dashboard',
    component: () => import('@/views/patient/PatientOverviewView.vue'),
    meta: { title: 'Tổng quan bệnh nhân' }
  },
  {
    path: 'appointments',
    name: 'patient-appointments',
    component: () => import('@/views/patient/PatientAppointmentsView.vue'),
    meta: { title: 'Lịch hẹn của tôi' }
  },
  {
    path: 'appointments/:id',
    name: 'patient-appointment-detail',
    component: () => import('@/views/patient/AppointmentDetail.vue'),
    meta: { title: 'Chi tiết lịch hẹn' }
  },
  {
    path: 'booking',
    name: 'patient-booking',
    component: () => import('@/views/patient/booking/BookingView.vue'),
    meta: { title: 'Đặt lịch khám' }
  },
  {
    path: 'records',
    name: 'patient-records',
    component: () => import('@/views/patient/PatientRecordsView.vue'),
    meta: { title: 'Bệnh án của tôi' }
  },
  {
    path: 'invoices',
    name: 'patient-invoices',
    component: () => import('@/views/patient/PatientInvoicesView.vue'),
    meta: { title: 'Hóa đơn của tôi' }
  },
  {
    path: 'notifications',
    name: 'patient-notifications',
    component: () => import('@/views/patient/PatientNotificationsView.vue'),
    meta: { title: 'Thông báo của tôi' }
  },
  {
    path: 'profile',
    name: 'patient-profile',
    component: () => import('@/views/patient/PatientProfileView.vue'),
    meta: { title: 'Hồ sơ bệnh nhân' }
  }
]
