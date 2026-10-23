import http from './http'

export const doctorSchedulesApi = {
  list: (params = {}) => http.get('/doctor-schedules', { params }).then((res) => res.data),
  get: (id) => http.get(`/doctor-schedules/${id}`).then((res) => res.data),
  create: (payload) => http.post('/doctor-schedules', payload).then((res) => res.data),
  update: (id, payload) => http.put(`/doctor-schedules/${id}`, payload).then((res) => res.data),
  remove: (id) => http.delete(`/doctor-schedules/${id}`),
  // Slot của một ca: [{ id, startTime, endTime, status, appointmentId }]
  slots: (id) => http.get(`/doctor-schedules/${id}/slots`).then((res) => res.data),
  addBreak: (id, payload) => http.post(`/doctor-schedules/${id}/breaks`, payload).then((res) => res.data),
  removeBreak: (scheduleId, breakId) =>
    http.delete(`/doctor-schedules/${scheduleId}/breaks/${breakId}`).then((res) => res.data),
  // Ngày nghỉ: [{ id, date, reason, createdAt }]
  daysOff: (params = {}) => http.get('/doctor-schedules/days-off', { params }).then((res) => res.data),
  addDayOff: (payload) => http.post('/doctor-schedules/days-off', payload).then((res) => res.data),
  removeDayOff: (id) => http.delete(`/doctor-schedules/days-off/${id}`)
}
