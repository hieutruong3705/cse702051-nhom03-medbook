import http from './http'

export const slotsApi = {
  // Có `serviceId` thì máy chủ chỉ trả khung giờ đủ dài cho dịch vụ đó; bỏ trống thì trả mọi khung giờ còn trống.
  availableByDoctor: (doctorId, date, serviceId) =>
    http.get('/appointment-slots/available', { params: { doctorId, date, serviceId } }).then((res) => res.data),
  // Các khung giờ sớm nhất đặt được cho một dịch vụ: { serviceId, specialtyId?, doctorId?, from?, limit? }.
  earliest: (params = {}) => http.get('/appointment-slots/earliest', { params }).then((res) => res.data),
  // Giữ chỗ có thời hạn: trả { holdId, slotId, expiresAt, remainingSeconds }.
  hold: (slotId) => http.post(`/appointment-slots/${slotId}/hold`).then((res) => res.data),
  release: (slotId) => http.delete(`/appointment-slots/${slotId}/hold`).then(() => null)
}
