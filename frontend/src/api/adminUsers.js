import http from './http'

export const adminUsersApi = {
  list: (params = {}) => http.get('/admin/users', { params }).then((res) => res.data),
  create: (payload) => http.post('/admin/users', payload).then((res) => res.data),
  get: (id) => http.get(`/admin/users/${id}`).then((res) => res.data),
  update: (id, payload) => http.put(`/admin/users/${id}`, payload).then((res) => res.data),
  changeStatus: (id, payload) => http.patch(`/admin/users/${id}/status`, payload).then((res) => res.data),
  changeRoles: (id, payload) => http.put(`/admin/users/${id}/roles`, payload).then((res) => res.data)
}
