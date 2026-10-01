import http from './http'

export const doctorSchedulesApi = {
  list: (params = {}) => http.get('/doctor-schedules', { params }).then((res) => res.data),
  create: (payload) => http.post('/doctor-schedules', payload).then((res) => res.data),
  update: (id, payload) => http.put(`/doctor-schedules/${id}`, payload).then((res) => res.data),
  remove: (id) => http.delete(`/doctor-schedules/${id}`),
  addBreak: (id, payload) => http.post(`/doctor-schedules/${id}/breaks`, payload).then((res) => res.data),
  removeBreak: (scheduleId, breakId) => http.delete(`/doctor-schedules/${scheduleId}/breaks/${breakId}`)
}
