import http from './http'

export const invoicesApi = {
  createForEncounter: (encounterId, payload) => http.post(`/encounters/${encounterId}/invoice`, payload).then((res) => res.data),
  mine: (params = {}) => http.get('/invoices/me', { params }).then((res) => res.data),
  get: (id) => http.get(`/invoices/${id}`).then((res) => res.data),
  listAdmin: (params = {}) => http.get('/admin/invoices', { params }).then((res) => res.data),
  collect: (id, payload = {}) => http.patch(`/admin/invoices/${id}/collect`, payload).then((res) => res.data),
  void: (id, payload = {}) => http.patch(`/admin/invoices/${id}/void`, payload).then((res) => res.data)
}
