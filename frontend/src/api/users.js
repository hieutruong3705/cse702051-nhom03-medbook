import http from './http'

export const usersApi = {
  me: () => http.get('/users/me').then((res) => res.data),
  updateMe: (payload) => http.put('/users/me', payload).then((res) => res.data)
}
