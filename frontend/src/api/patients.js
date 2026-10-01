import http from './http'

export const patientsApi = {
  me: () => http.get('/patients/me').then((res) => res.data),
  updateMe: (payload) => http.put('/patients/me', payload).then((res) => res.data),
  list: (params = {}) => http.get('/patients', { params }).then((res) => res.data),
  get: (id) => http.get(`/patients/${id}`).then((res) => res.data)
}
