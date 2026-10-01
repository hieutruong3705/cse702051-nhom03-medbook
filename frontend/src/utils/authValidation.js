/**
 * Quy tắc kiểm tra dữ liệu của các form xác thực — khớp với backend (`RegisterRequest`,
 * `PasswordPolicy`). Backend vẫn là nguồn chuẩn; kiểm tra ở đây chỉ để báo lỗi sớm, đúng trường.
 * Mỗi hàm trả về chuỗi lỗi hoặc '' nếu hợp lệ.
 */

export const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/
export const PASSWORD_MESSAGE = 'Mật khẩu phải từ 8 đến 100 ký tự, gồm ít nhất một chữ cái và một chữ số'

const USERNAME_RULE = /^[A-Za-z0-9._-]{3,50}$/
const EMAIL_RULE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RULE = /^[0-9+()\-\s]{8,30}$/

export const required = (label) => (value) => (String(value ?? '').trim() ? '' : `${label} không được để trống`)

export const validateUsername = (value) => {
  if (!String(value ?? '').trim()) return 'Tên đăng nhập không được để trống'
  return USERNAME_RULE.test(value.trim())
    ? ''
    : 'Tên đăng nhập từ 3 đến 50 ký tự, chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới, gạch ngang'
}

export const validateEmail = (value) => {
  if (!String(value ?? '').trim()) return 'Email không được để trống'
  return EMAIL_RULE.test(value.trim()) && value.trim().length <= 190 ? '' : 'Định dạng email không hợp lệ'
}

export const validatePhone = (value) => {
  if (!String(value ?? '').trim()) return 'Số điện thoại không được để trống'
  return PHONE_RULE.test(value.trim()) ? '' : 'Số điện thoại không hợp lệ'
}

export const validatePassword = (value) => {
  if (!value) return 'Mật khẩu không được để trống'
  return PASSWORD_RULE.test(value) ? '' : PASSWORD_MESSAGE
}

/** Xác nhận mật khẩu phải trùng với trường `other` trong cùng form. */
export const validateConfirm = (other = 'password') => (value, values) => {
  if (!value) return 'Vui lòng nhập lại mật khẩu'
  return value === values[other] ? '' : 'Mật khẩu nhập lại không khớp'
}

/** Ngày sinh (nếu có) phải ở trong quá khứ. */
export const validateBirthDate = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.valueOf())) return 'Ngày sinh không hợp lệ'
  return date.getTime() < Date.now() ? '' : 'Ngày sinh phải ở trong quá khứ'
}
