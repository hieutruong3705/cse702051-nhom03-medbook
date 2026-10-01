import http from './http'

export const authApi = {
  register: (payload) => http.post('/auth/register', payload).then((res) => res.data),
  login: (payload) => http.post('/auth/login', payload).then((res) => res.data),
  logout: () => http.post('/auth/logout'),
  changePassword: (payload) => http.post('/auth/change-password', payload),
  forgotPassword: (payload) => http.post('/auth/forgot-password', payload),
  resetPassword: (payload) => http.post('/auth/reset-password', payload)
}
