import http from './http'

export const clinicApi = {
  get: () => http.get('/clinic').then((res) => res.data)
}
