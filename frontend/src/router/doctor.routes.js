// Route con của khu bác sĩ (/doctor).
export default [
  {
    path: 'dashboard',
    name: 'doctor-dashboard',
    component: () => import('@/views/doctor/DoctorOverviewView.vue'),
    meta: { title: 'Tổng quan bác sĩ' }
  },
  {
    path: 'schedules',
    name: 'doctor-schedules',
    component: () => import('@/views/doctor/DoctorScheduleView.vue'),
    meta: { title: 'Lịch làm việc' }
  },
  {
    path: 'appointments',
    name: 'doctor-appointments',
    component: () => import('@/views/doctor/DoctorAppointmentsView.vue'),
    meta: { title: 'Lịch khám' }
  },
  {
    path: 'encounters/:id',
    name: 'doctor-encounter',
    component: () => import('@/views/doctor/encounter/DoctorEncounterView.vue'),
    meta: { title: 'Khám bệnh' }
  },
  {
    path: 'patients',
    name: 'doctor-patients',
    component: () => import('@/views/doctor/DoctorPatientsView.vue'),
    meta: { title: 'Bệnh nhân' }
  },
  {
    path: 'patients/:id(\\d+)',
    name: 'doctor-patient-detail',
    component: () => import('@/views/doctor/DoctorPatientDetailView.vue'),
    meta: { title: 'Chi tiết bệnh nhân' }
  },
  {
    path: 'notifications',
    name: 'doctor-notifications',
    component: () => import('@/views/doctor/DoctorNotificationsView.vue'),
    meta: { title: 'Thông báo' }
  },
  {
    path: 'profile',
    name: 'doctor-profile',
    component: () => import('@/views/doctor/DoctorProfileView.vue'),
    meta: { title: 'Hồ sơ bác sĩ' }
  }
]
