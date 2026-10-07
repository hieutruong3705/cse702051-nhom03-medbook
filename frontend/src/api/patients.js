import http from './http'

// `quiet: true` để trang tự hiển thị lỗi 403 (bệnh nhân ngoài phạm vi phụ trách) thay vì bị chuyển sang trang 403 chung.
const quiet = { skipGlobalErrors: true }

export const patientsApi = {
  me: () => http.get('/patients/me').then((res) => res.data),
  updateMe: (payload) => http.put('/patients/me', payload).then((res) => res.data),
  list: (params = {}) => http.get('/patients', { params }).then((res) => res.data),
  get: (id) => http.get(`/patients/${id}`, quiet).then((res) => res.data)
}
