import { clinicApi } from '@/api/clinic'
import { catalogApi } from '@/api/catalog'

// Địa chỉ phòng khám và giá dịch vụ thấp nhất: dùng chung cho mọi thẻ bác sĩ trên một trang, chỉ tải một lần.
let pending = null

export function loadClinicFacts() {
  if (!pending) {
    pending = Promise.all([
      clinicApi.get().catch(() => null),
      catalogApi.services.list({ size: 100 }).catch(() => null)
    ]).then(([clinic, services]) => {
      const prices = (services?.content ?? []).map((item) => Number(item.price)).filter((price) => price > 0)
      return {
        name: clinic?.name ?? '',
        address: clinic?.address ?? '',
        minPrice: prices.length ? Math.min(...prices) : null
      }
    })
  }
  return pending
}

/** Chỉ dùng trong test: xóa dữ liệu đã nạp. */
export function resetClinicFacts() {
  pending = null
}
