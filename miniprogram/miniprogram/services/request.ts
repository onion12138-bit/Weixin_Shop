interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  data?: Record<string, unknown>
}

/**
 * 保留后端业务码与追踪号的小程序请求异常。
 */
export class ApiError extends Error {
  readonly code: string
  readonly traceId?: string

  constructor(message: string, code: string, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

/**
 * 返回当前环境的 API 根地址。
 */
export function apiBaseUrl(): string {
  const app = getApp<{
    globalData: { apiBaseUrl: string }
  }>()
  return app.globalData.apiBaseUrl.replace(/\/$/, '')
}

/**
 * 将后端相对文件地址转换为小程序可以请求的完整 URL。
 */
export function absolutePublicUrl(url: string | null): string | null {
  if (!url || /^https?:\/\//.test(url)) return url
  const origin = apiBaseUrl().replace(/\/api$/, '')
  return `${origin}${url.startsWith('/') ? '' : '/'}${url}`
}

/**
 * 调用后端统一响应接口，非 OK 响应转换为 ApiError。
 */
export function request<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  return new Promise((resolve, reject) => {
    wx.request<ApiEnvelope<T>>({
      url: `${apiBaseUrl()}${path}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: 10000,
      success(response) {
        const envelope = response.data
        if (
          response.statusCode >= 200 &&
          response.statusCode < 300 &&
          envelope.code === 'OK'
        ) {
          resolve(envelope.data)
          return
        }
        reject(
          new ApiError(
            envelope?.message || '请求失败',
            envelope?.code || 'COMMON_INTERNAL_ERROR',
            envelope?.traceId,
          ),
        )
      },
      fail(error) {
        reject(new ApiError(error.errMsg || '网络连接失败', 'NETWORK_ERROR'))
      },
    })
  })
}
