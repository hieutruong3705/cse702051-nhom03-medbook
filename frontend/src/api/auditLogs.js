import http from './http'

export const auditLogsApi = {
  list: (params = {}) => http.get('/admin/audit-logs', { params }).then((res) => res.data),
  actionCodes: () => http.get('/admin/audit-logs/action-codes').then((res) => res.data)
}
