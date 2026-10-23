export function apiErrorMessage(error) {
  if (!error) return 'Không thể xử lý yêu cầu.'
  if (typeof error === 'string') return error
  if (error.message && !error.status) return error.message
  // Tài khoản bị khóa (đăng nhập sai quá số lần / Admin khóa): hiển thị đúng thông điệp từ server.
  if (error.code === 'ACCOUNT_LOCKED') return error.message || 'Tài khoản đang bị khóa.'
  if (error.status === 409) return error.message || 'Dữ liệu đã thay đổi, vui lòng thử lại.'
  if (error.status === 422) return error.message || 'Dữ liệu chưa hợp lệ.'
  if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
  if (error.status >= 500) return 'Hệ thống đang bận, vui lòng thử lại sau.'
  return error.message || 'Không thể xử lý yêu cầu.'
}
