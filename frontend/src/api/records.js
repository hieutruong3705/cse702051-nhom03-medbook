import http from './http'

export const recordsApi = {
  mine: () => http.get('/medical-records/me').then((res) => res.data),
  get: (id) => http.get(`/medical-records/${id}`).then((res) => res.data),
  // Bệnh án của một bệnh nhân, cho bác sĩ phụ trách. 403 do trang gọi tự xử lý.
  byPatient: (patientId) =>
    http.get(`/patients/${patientId}/medical-records`, { skipGlobalErrors: true }).then((res) => res.data)
}
