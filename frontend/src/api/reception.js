import http from './http'

export const receptionApi = {
  overview: (params = {}) => http.get('/reception/overview', { params }).then((res) => res.data),
  patients: {
    list: (params = {}) => http.get('/reception/patients', { params }).then((res) => res.data),
    get: (id) => http.get(`/reception/patients/${id}`).then((res) => res.data),
    create: (payload) => http.post('/reception/patients', payload).then((res) => res.data)
  },
  appointments: {
    list: (params = {}) => http.get('/reception/appointments', { params }).then((res) => res.data),
    get: (id) => http.get(`/reception/appointments/${id}`).then((res) => res.data),
    create: (payload) => http.post('/reception/appointments', payload).then((res) => res.data),
    cancel: (id, payload) => http.patch(`/reception/appointments/${id}/cancel`, payload).then((res) => res.data),
    reschedule: (id, payload) => http.patch(`/reception/appointments/${id}/reschedule`, payload).then((res) => res.data)
  },
  invoices: {
    list: (params = {}) => http.get('/reception/invoices', { params }).then((res) => res.data),
    items: (id) => http.get(`/reception/invoices/${id}/items`).then((res) => res.data),
    collect: (id) => http.patch(`/reception/invoices/${id}/collect`).then((res) => res.data)
  }
}
