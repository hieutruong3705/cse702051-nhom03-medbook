import http from './http'

export const notificationsApi = {
  mine: (params = {}) => http.get('/notifications/me', { params }).then((res) => res.data),
  markRead: (id) => http.patch(`/notifications/${id}/read`).then((res) => res.data)
}
