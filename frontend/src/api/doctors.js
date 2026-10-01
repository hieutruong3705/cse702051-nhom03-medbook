import http from './http'

export const doctorsApi = {
  list: (params = {}) => http.get('/doctors', { params }).then((res) => res.data),
  get: (id) => http.get(`/doctors/${id}`).then((res) => res.data),
  listAdmin: (params = {}) => http.get('/admin/doctors', { params }).then((res) => res.data),
  createAdmin: (payload) => http.post('/admin/doctors', payload).then((res) => res.data),
  updateAdmin: (id, payload) => http.put(`/admin/doctors/${id}`, payload).then((res) => res.data),
  removeAdmin: (id) => http.delete(`/admin/doctors/${id}`)
}
