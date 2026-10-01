import http from './http'

export const encountersApi = {
  create: (payload) => http.post('/encounters', payload).then((res) => res.data),
  mine: (params = {}) => http.get('/encounters/me', { params }).then((res) => res.data),
  list: (params = {}) => http.get('/encounters', { params }).then((res) => res.data),
  get: (id) => http.get(`/encounters/${id}`).then((res) => res.data),
  update: (id, payload) => http.put(`/encounters/${id}`, payload).then((res) => res.data)
}
