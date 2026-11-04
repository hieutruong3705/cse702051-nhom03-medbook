import http from './http'

export const doctorsApi = {
  list: (params = {}) => http.get('/doctors', { params }).then((res) => res.data),
  get: (id) => http.get(`/doctors/${id}`).then((res) => res.data),
  // Giờ trống công khai của một bác sĩ trong một ngày (YYYY-MM-DD), không cần đăng nhập.
  slots: (id, date) => http.get(`/doctors/${id}/slots`, { params: { date } }).then((res) => res.data),
  // Bác sĩ đang đăng nhập: hồ sơ của chính mình (có số giấy phép) và đánh giá về mình.
  myProfile: () => http.get('/doctors/me/profile').then((res) => res.data),
  updateMyProfile: (payload) => http.put('/doctors/me/profile', payload).then((res) => res.data),
  myReviews: (params = {}) => http.get('/doctors/me/reviews', { params }).then((res) => res.data),
  myRatingSummary: () => http.get('/doctors/me/rating-summary').then((res) => res.data),
  listAdmin: (params = {}) => http.get('/admin/doctors', { params }).then((res) => res.data),
  createAdmin: (payload) => http.post('/admin/doctors', payload).then((res) => res.data),
  updateAdmin: (id, payload) => http.put(`/admin/doctors/${id}`, payload).then((res) => res.data),
  // Trả về hồ sơ (isActive = false) khi bác sĩ đã có lịch sử nên chỉ ngừng hoạt động; rỗng khi đã xóa hẳn.
  removeAdmin: (id) => http.delete(`/admin/doctors/${id}`).then((res) => res.data || null)
}
