import http from './http'

// `list` là danh mục công khai (chỉ mục đang hoạt động); `listAdmin` gồm cả mục đã ngừng dùng, chỉ Admin gọi được.
// `remove` trả về bản ghi (trạng thái INACTIVE) khi mục đang được tham chiếu nên chỉ ngừng dùng, và rỗng khi đã xóa hẳn.
const resource = (path) => ({
  list: (params = {}) => http.get(path, { params }).then((res) => res.data),
  get: (id) => http.get(`${path}/${id}`).then((res) => res.data),
  listAdmin: (params = {}) => http.get(`/admin${path}`, { params }).then((res) => res.data),
  create: (payload) => http.post(`/admin${path}`, payload).then((res) => res.data),
  update: (id, payload) => http.put(`/admin${path}/${id}`, payload).then((res) => res.data),
  remove: (id) => http.delete(`/admin${path}/${id}`).then((res) => res.data || null)
})

const imageForm = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return formData
}

export const catalogApi = {
  specialties: {
    ...resource('/specialties'),
    // Ảnh chuyên khoa (JPEG, PNG, WebP, tối đa 1 MB), chỉ quản trị viên
    uploadImage: (id, file) => http.post(`/admin/specialties/${id}/image`, imageForm(file)).then((res) => res.data),
    removeImage: (id) => http.delete(`/admin/specialties/${id}/image`).then((res) => res.data)
  },
  services: resource('/medical-services'),
  medicines: resource('/medicines')
}
