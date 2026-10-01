import http from './http'

export const appointmentsApi = {
  create: (payload) => http.post('/appointments', payload).then((res) => res.data),
  mine: (params = {}) => http.get('/appointments/me', { params }).then((res) => res.data),
  get: (id) => http.get(`/appointments/${id}`).then((res) => res.data),
  cancel: (id, payload) => http.patch(`/appointments/${id}/cancel`, payload).then((res) => res.data),
  reschedule: (id, payload) => http.patch(`/appointments/${id}/reschedule`, payload).then((res) => res.data),
  updateStatus: (id, payload) => http.patch(`/appointments/${id}/status`, payload).then((res) => res.data),
  listAdmin: (params = {}) => http.get('/admin/appointments', { params }).then((res) => res.data)
}
