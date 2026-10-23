import http from './http'

export const invoicesApi = {
  // `payload` là mảng dòng hóa đơn [{ serviceId, quantity, description? }]; `params` = { discountAmount }.
  // Đơn giá do server lấy từ danh mục dịch vụ nên không gửi.
  createForEncounter: (encounterId, payload, params = {}) =>
    http.post(`/encounters/${encounterId}/invoice`, payload, { params }).then((res) => res.data),
  // Hóa đơn của một lần khám (bác sĩ phụ trách hoặc bệnh nhân chủ); 404 khi chưa lập.
  byEncounter: (encounterId) => http.get(`/encounters/${encounterId}/invoice`).then((res) => res.data),
  items: (id) => http.get(`/invoices/${id}/items`).then((res) => res.data),
  mine: (params = {}) => http.get('/invoices/me', { params }).then((res) => res.data),
  get: (id) => http.get(`/invoices/${id}`).then((res) => res.data),
  listAdmin: (params = {}) => http.get('/admin/invoices', { params }).then((res) => res.data),
  collect: (id, payload = {}) => http.patch(`/admin/invoices/${id}/collect`, payload).then((res) => res.data),
  void: (id, payload = {}) => http.patch(`/admin/invoices/${id}/void`, payload).then((res) => res.data)
}
