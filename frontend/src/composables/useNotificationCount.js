import { ref } from 'vue'
import { notificationsApi } from '@/api/notifications'

// Số thông báo chưa đọc dùng chung cho chuông ở thanh trên và trang thông báo.
const count = ref(0)

export function useNotificationCount() {
  const refresh = async () => {
    try {
      const response = await notificationsApi.unreadCount()
      count.value = Number(response?.count ?? 0)
    } catch {
      // Chuông chỉ là tiện ích: lỗi mạng ở đây không được làm phiền người dùng.
    }
  }
  return { count, refresh }
}
