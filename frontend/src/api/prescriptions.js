import http from './http'

export const prescriptionsApi = {
  createForEncounter: (encounterId, payload) => http.post(`/encounters/${encounterId}/prescriptions`, payload).then((res) => res.data),
  listByEncounter: (encounterId) => http.get(`/encounters/${encounterId}/prescriptions`).then((res) => res.data),
  update: (id, payload) => http.put(`/prescriptions/${id}`, payload).then((res) => res.data)
}
