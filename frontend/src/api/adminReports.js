import http from './http'

const blob = (path, params) => http.get(path, { params, responseType: 'blob' }).then((res) => res.data)

// Ba báo cáo của Admin; mỗi báo cáo có bản xuất CSV (trả Blob).
export const adminReportsApi = {
  appointments: (params = {}) => http.get('/admin/reports/appointments', { params }).then((res) => res.data),
  exportAppointments: (params = {}) => blob('/admin/reports/appointments/export', params),
  revenue: (params = {}) => http.get('/admin/reports/revenue', { params }).then((res) => res.data),
  exportRevenue: (params = {}) => blob('/admin/reports/revenue/export', params),
  services: (params = {}) => http.get('/admin/reports/services', { params }).then((res) => res.data),
  exportServices: (params = {}) => blob('/admin/reports/services/export', params)
}
