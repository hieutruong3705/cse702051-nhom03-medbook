import http from './http'

export const recordsApi = {
  mine: () => http.get('/medical-records/me').then((res) => res.data),
  get: (id) => http.get(`/medical-records/${id}`).then((res) => res.data),
  byPatient: (patientId) => http.get(`/patients/${patientId}/medical-records`).then((res) => res.data)
}
