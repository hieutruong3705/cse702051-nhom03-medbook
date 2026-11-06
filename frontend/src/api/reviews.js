import http from './http'

// Đánh giá lần khám. `listPublic`, `forDoctor`, `summary` công khai; `create`, `update`, `remove`, `mine`,
// `forAppointment` chỉ bệnh nhân; `listAdmin`, `hide`, `show` chỉ quản trị viên.
export const reviewsApi = {
  listPublic: (params = {}) => http.get('/reviews', { params }).then((res) => res.data),
  forDoctor: (doctorId, params = {}) => http.get(`/doctors/${doctorId}/reviews`, { params }).then((res) => res.data),
  summary: (doctorId) => http.get(`/doctors/${doctorId}/rating-summary`).then((res) => res.data),
  mine: (params = {}) => http.get('/reviews/me', { params }).then((res) => res.data),
  // 404 nghĩa là lần khám chưa được đánh giá: trang tự xử lý, không hiện thông báo lỗi chung.
  forAppointment: (appointmentId) =>
    http.get(`/appointments/${appointmentId}/review`, { skipGlobalErrors: true }).then((res) => res.data),
  create: (appointmentId, payload) => http.post(`/appointments/${appointmentId}/review`, payload).then((res) => res.data),
  update: (id, payload) => http.put(`/reviews/${id}`, payload).then((res) => res.data),
  remove: (id) => http.delete(`/reviews/${id}`),
  listAdmin: (params = {}) => http.get('/admin/reviews', { params }).then((res) => res.data),
  hide: (id, reason) => http.patch(`/admin/reviews/${id}/hide`, { reason }).then((res) => res.data),
  show: (id) => http.patch(`/admin/reviews/${id}/show`).then((res) => res.data)
}
