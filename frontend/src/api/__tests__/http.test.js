import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { ApiRequestError, configureHttp, http, isApiError } from '@/api/http'

/** Thay adapter mạng của axios bằng hàm giả; status >= 400 bị từ chối giống axios thật. */
function useAdapter(handler) {
  http.defaults.adapter = async (config) => {
    const result = await handler(config)
    const response = {
      data: result?.data ?? null,
      status: result?.status ?? 200,
      statusText: '',
      headers: {},
      config
    }
    if (response.status >= 400) {
      throw new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, null, response)
    }
    return response
  }
}

const apiError = (status, code, message, extra = {}) => ({
  status,
  data: {
    timestamp: '2026-09-30T10:00:00Z',
    status,
    error: 'Error',
    code,
    message,
    path: '/api/v1/x',
    details: {},
    ...extra
  }
})

describe('http client', () => {
  let onUnauthorized
  let onForbidden
  let token

  beforeEach(() => {
    vi.useFakeTimers()
    token = null
    onUnauthorized = vi.fn()
    onForbidden = vi.fn()
    configureHttp({ getToken: () => token, refreshToken: null, onUnauthorized, onForbidden })
  })

  afterEach(() => {
    vi.advanceTimersByTime(1500) // xả cờ chống lặp 401
    vi.useRealTimers()
  })

  it('gắn header Bearer khi có token và không gắn khi chưa đăng nhập', async () => {
    const seen = []
    useAdapter((config) => {
      seen.push(config.headers.Authorization ?? null)
      return { data: {} }
    })

    await http.get('/doctors')
    token = 'abc.def.ghi'
    await http.get('/doctors')

    expect(seen).toEqual([null, 'Bearer abc.def.ghi'])
  })

  it('dùng VITE_API_BASE_URL hoặc /api/v1 làm base URL', () => {
    expect(http.defaults.baseURL).toBeTruthy()
    expect(http.defaults.baseURL.startsWith('/')).toBe(true)
  })

  it('chuẩn hóa ApiError của backend (status, code, message, details, path)', async () => {
    useAdapter(() =>
      apiError(409, 'CONFLICT', 'Khung giờ này đã được đặt', { details: { slotId: 'đã đặt' } })
    )

    const error = await http.post('/appointments').catch((e) => e)

    expect(error).toBeInstanceOf(ApiRequestError)
    expect(error.status).toBe(409)
    expect(error.code).toBe('CONFLICT')
    expect(error.message).toBe('Khung giờ này đã được đặt')
    expect(error.details).toEqual({ slotId: 'đã đặt' })
    expect(error.path).toBe('/api/v1/x')
    expect(isApiError(error, 'CONFLICT')).toBe(true)
    expect(isApiError(error, 'FORBIDDEN')).toBe(false)
  })

  it('409/422/400 được trả nguyên, không kích hoạt xử lý 401/403 toàn cục', async () => {
    for (const status of [400, 404, 409, 422]) {
      useAdapter(() => apiError(status, 'X', 'lỗi nghiệp vụ'))
      await expect(http.get('/x')).rejects.toMatchObject({ status })
    }
    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(onForbidden).not.toHaveBeenCalled()
  })

  it('lỗi 5xx không phải JSON được đổi thành thông điệp thân thiện', async () => {
    useAdapter(() => ({ status: 502, data: '<html>Bad gateway</html>' }))

    const error = await http.get('/x').catch((e) => e)

    expect(error.status).toBe(502)
    expect(error.code).toBe('HTTP_502')
    expect(error.message).toMatch(/Máy chủ/)
  })

  it('mất mạng → status 0, NETWORK_ERROR', async () => {
    http.defaults.adapter = async (config) => {
      throw new AxiosError('Network Error', AxiosError.ERR_NETWORK, config)
    }

    const error = await http.get('/x').catch((e) => e)

    expect(error).toBeInstanceOf(ApiRequestError)
    expect(error.status).toBe(0)
    expect(error.isNetworkError).toBe(true)
    expect(error.code).toBe('NETWORK_ERROR')
  })

  it('quá thời gian chờ → TIMEOUT', async () => {
    http.defaults.adapter = async (config) => {
      throw new AxiosError('timeout', AxiosError.ECONNABORTED, config)
    }

    const error = await http.get('/x').catch((e) => e)

    expect(error.code).toBe('TIMEOUT')
  })

  it('401 gọi onUnauthorized đúng một lần dù nhiều request song song cùng lỗi', async () => {
    useAdapter(() => apiError(401, 'UNAUTHORIZED', 'Hết phiên'))

    await Promise.allSettled([http.get('/a'), http.get('/b'), http.get('/c')])

    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('401 của đăng nhập/đăng ký (sai mật khẩu) không bị coi là hết phiên', async () => {
    useAdapter(() => apiError(401, 'UNAUTHORIZED', 'Sai tên đăng nhập hoặc mật khẩu'))

    await expect(http.post('/auth/login', {})).rejects.toMatchObject({ status: 401 })
    await expect(http.post('/auth/register', {})).rejects.toMatchObject({ status: 401 })

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('cấu hình skipGlobalErrors tắt xử lý toàn cục cho một request', async () => {
    useAdapter(() => apiError(401, 'UNAUTHORIZED', 'Hết phiên'))

    await expect(http.post('/auth/logout', null, { skipGlobalErrors: true })).rejects.toMatchObject({ status: 401 })

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('403 gọi onForbidden (kèm cấu hình request) nhưng ACCOUNT_LOCKED thì không', async () => {
    useAdapter(() => apiError(403, 'FORBIDDEN', 'Không có quyền'))
    await expect(http.get('/admin/invoices')).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' })
    expect(onForbidden).toHaveBeenCalledTimes(1)
    expect(onForbidden.mock.calls[0][1].method).toBe('get')

    useAdapter(() => apiError(403, 'ACCOUNT_LOCKED', 'Tài khoản bị khóa 15 phút'))
    await expect(http.get('/users/me')).rejects.toMatchObject({ code: 'ACCOUNT_LOCKED' })
    expect(onForbidden).toHaveBeenCalledTimes(1)
  })

  it('lỗi khi tải tệp (responseType blob) vẫn được đọc thành ApiError', async () => {
    const body = JSON.stringify({ code: 'FORBIDDEN', message: 'Không được tải tệp này', path: '/attachments/9' })
    http.defaults.adapter = async (config) => {
      throw new AxiosError('failed', AxiosError.ERR_BAD_REQUEST, config, null, {
        status: 403,
        data: new Blob([body], { type: 'application/json' }),
        headers: {},
        config
      })
    }

    const error = await http.get('/attachments/9/download', { responseType: 'blob' }).catch((e) => e)

    expect(error.status).toBe(403)
    expect(error.code).toBe('FORBIDDEN')
    expect(error.message).toBe('Không được tải tệp này')
  })
})
