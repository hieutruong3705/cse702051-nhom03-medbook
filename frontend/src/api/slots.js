import http from './http'

export const slotsApi = {
  availableByDoctor: (doctorId, date) => http.get('/appointment-slots/available', { params: { doctorId, date } }).then((res) => res.data)
}
