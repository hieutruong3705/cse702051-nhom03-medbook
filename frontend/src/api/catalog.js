import http from './http'

const resource = (path) => ({
  list: (params = {}) => http.get(path, { params }).then((res) => res.data),
  create: (payload) => http.post(`/admin${path}`, payload).then((res) => res.data),
  update: (id, payload) => http.put(`/admin${path}/${id}`, payload).then((res) => res.data),
  remove: (id) => http.delete(`/admin${path}/${id}`)
})

export const catalogApi = {
  specialties: resource('/specialties'),
  services: resource('/medical-services'),
  medicines: resource('/medicines')
}
