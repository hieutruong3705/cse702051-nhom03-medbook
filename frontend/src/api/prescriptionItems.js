import http from './http'

/** Dòng thuốc của một đơn thuốc (bác sĩ phụ trách ghi; bệnh nhân chủ lần khám chỉ đọc). */
export const prescriptionItemsApi = {
  listByPrescription: (prescriptionId) => http.get(`/prescriptions/${prescriptionId}/items`).then((res) => res.data),
  create: (prescriptionId, payload) => http.post(`/prescriptions/${prescriptionId}/items`, payload).then((res) => res.data),
  remove: (id) => http.delete(`/prescription-items/${id}`)
}
