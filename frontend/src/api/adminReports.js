import http from './http'

export const adminReportsApi = {
  appointments: (params = {}) => http.get('/admin/reports/appointments', { params }).then((res) => res.data),
  exportAppointments: (params = {}) => http.get('/admin/reports/appointments/export', { params, responseType: 'blob' }).then((res) => res.data),
  revenue: (params = {}) => http.get('/admin/reports/revenue', { params }).then((res) => res.data)
}
