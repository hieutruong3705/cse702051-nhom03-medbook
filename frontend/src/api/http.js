import axios, { AxiosError } from 'axios'

/**
 * Instance axios DUY NHẤT của ứng dụng.
 *
 * Quy ước:
 * - Base URL lấy từ VITE_API_BASE_URL (mặc định /api/v1, qua proxy của Vite).
 * - Lỗi luôn được chuẩn hóa thành {@link ApiRequestError} (khớp `ApiError` của backend):
 *   { status, code, message, details, path, timestamp }.
 * - 401 → gọi handler `onUnauthorized` (store auth đăng xuất và về /login).
 * - 403 → gọi handler `onForbidden` (toast / trang 403), trừ ACCOUNT_LOCKED.
 * - 409/422/400... được trả nguyên cho nơi gọi tự xử lý theo ngữ cảnh.
 *
 * Với từng request có thể tắt xử lý toàn cục bằng cấu hình `skipGlobalErrors: true`.
 * Module này KHÔNG import store để tránh phụ thuộc vòng; store đăng ký handler qua
 * {@link configureHttp}.
 */

export const NETWORK_ERROR = 'NETWORK_ERROR'
export const TIMEOUT_ERROR = 'TIMEOUT'

export class ApiRequestError extends Error {
  constructor({ status = 0, code = 'UNKNOWN_ERROR', message, details = {}, path = '', timestamp = null, cause } = {}) {
    super(message || 'Đã xảy ra lỗi. Vui lòng thử lại.')
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.details = details || {}
    this.path = path
    this.timestamp = timestamp
    if (cause) this.cause = cause
  }

  get isNetworkError() {
    return this.status === 0
  }
}

export function isApiError(error, code) {
  return error instanceof ApiRequestError && (code === undefined || error.code === code)
}

const handlers = {
  getToken: () => null,
  refreshToken: null,
  onUnauthorized: () => {},
  onForbidden: () => {}
}

/**
 * Đăng ký các hàm phụ thuộc phiên đăng nhập. Chỉ truyền các khóa cần đổi.
 * Handler nhận thêm cấu hình axios của request lỗi (để biết method/url).
 * @param {{ getToken?: () => (string|null),
 *           onUnauthorized?: (error: ApiRequestError, config?: object) => void,
 *           onForbidden?: (error: ApiRequestError, config?: object) => void }} config
 */
export function configureHttp(config = {}) {
  Object.assign(handlers, config)
}

export const http = axios.create({
  baseURL: import.meta.env?.VITE_API_BASE_URL || '/api/v1',
  timeout: 20000,
  headers: { Accept: 'application/json' }
})

http.interceptors.request.use((config) => {
  const token = handlers.getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

async function readBody(response) {
  const data = response?.data
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    // Lỗi trả về khi request có responseType 'blob' (ví dụ tải tệp) vẫn là JSON ApiError.
    try {
      return JSON.parse(await data.text())
    } catch {
      return null
    }
  }
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return null
    }
  }
  return data && typeof data === 'object' ? data : null
}

/**
 * Chuyển mọi lỗi axios thành ApiRequestError.
 * @param {unknown} error
 * @returns {Promise<ApiRequestError>}
 */
export async function normalizeError(error) {
  if (error instanceof ApiRequestError) return error

  if (!axios.isAxiosError(error)) {
    return new ApiRequestError({ message: error?.message, cause: error })
  }

  if (error.code === AxiosError.ECONNABORTED || error.code === AxiosError.ETIMEDOUT) {
    return new ApiRequestError({
      code: TIMEOUT_ERROR,
      message: 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.',
      cause: error
    })
  }

  if (!error.response) {
    return new ApiRequestError({
      code: NETWORK_ERROR,
      message: 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.',
      cause: error
    })
  }

  const { status } = error.response
  const body = await readBody(error.response)
  const fallback =
    status >= 500 ? 'Máy chủ gặp sự cố. Vui lòng thử lại sau.' : 'Yêu cầu không thể thực hiện.'

  return new ApiRequestError({
    status,
    code: body?.code || `HTTP_${status}`,
    message: body?.message || fallback,
    details: body?.details,
    path: body?.path || error.config?.url || '',
    timestamp: body?.timestamp || null,
    cause: error
  })
}

let unauthorizedNotified = false

// Các endpoint công khai của phần đăng nhập: 401/403 ở đây là kết quả nghiệp vụ
// (sai mật khẩu, tài khoản bị khóa) chứ không phải hết phiên, nên không xử lý toàn cục.
const PUBLIC_AUTH_URL = /\/auth\/(login|register|forgot-password|reset-password|refresh)\/?$/

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const apiError = await normalizeError(error)
    const skip = error?.config?.skipGlobalErrors === true || PUBLIC_AUTH_URL.test(error?.config?.url || '')

    if (!skip && apiError.status === 401) {
      if (!error.config?._refreshRetried && handlers.refreshToken) {
        error.config._refreshRetried = true
        try {
          const freshToken = await handlers.refreshToken(error.config.headers?.Authorization?.replace(/^Bearer /, ''))
          if (freshToken) {
            error.config.headers.Authorization = `Bearer ${freshToken}`
            return http.request(error.config)
          }
        } catch (refreshError) {
          return Promise.reject(refreshError)
        }
      }
      // Nhiều request song song cùng nhận 401 chỉ kích hoạt đăng xuất một lần.
      if (!unauthorizedNotified) {
        unauthorizedNotified = true
        setTimeout(() => {
          unauthorizedNotified = false
        }, 1000)
        handlers.onUnauthorized(apiError, error.config)
      }
    } else if (!skip && apiError.status === 403 && apiError.code !== 'ACCOUNT_LOCKED') {
      handlers.onForbidden(apiError, error.config)
    }

    return Promise.reject(apiError)
  }
)

export default http
