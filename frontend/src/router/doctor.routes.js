// Child routes under /doctor.
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
    path: 'patients',
    name: 'doctor-patients',
    component: () => import('@/views/doctor/DoctorPatientsView.vue'),
    meta: { title: 'Bệnh nhân' }
  },
  {
    path: 'profile',
    name: 'doctor-profile',
    component: () => import('@/views/doctor/DoctorProfileView.vue'),
    meta: { title: 'Hồ sơ bác sĩ' }
  }
]
