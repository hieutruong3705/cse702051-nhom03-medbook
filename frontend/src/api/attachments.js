import http from './http'

export const attachmentsApi = {
  upload: (encounterId, file) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post(`/encounters/${encounterId}/attachments`, formData).then((res) => res.data)
  },
  listByEncounter: (encounterId) => http.get(`/encounters/${encounterId}/attachments`).then((res) => res.data),
  download: (id) => http.get(`/attachments/${id}/download`, { responseType: 'blob' }).then((res) => res.data),
  remove: (id) => http.delete(`/attachments/${id}`)
}
