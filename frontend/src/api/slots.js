import http from './http'

export const slotsApi = {
  availableByDoctor: (doctorId, date) => http.get(`/doctors/${doctorId}/slots`, { params: { date } }).then((res) => res.data)
}
