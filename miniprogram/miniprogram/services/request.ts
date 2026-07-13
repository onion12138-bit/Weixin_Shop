import {
  ensureGuestToken,
  guestSessionManager,
  refreshGuestToken,
} from './session'

interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  data?: unknown
}

interface UploadEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
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
  retried = false,
): Promise<T> {
  return customerHeader(path).then((header) => new Promise<T>((resolve, reject) => {
    wx.request<ApiEnvelope<T>>({
      url: `${apiBaseUrl()}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header,
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
        if (shouldRefreshCustomerSession(path, response.statusCode, retried)) {
          guestSessionManager.invalidate()
          refreshGuestToken()
            .then(() => request<T>(path, options, true))
            .then(resolve, reject)
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
  }))
}

export function upload<T>(
  path: string,
  filePath: string,
  name = 'file',
  retried = false,
): Promise<T> {
  return ensureGuestToken().then((token) => new Promise<T>((resolve, reject) => {
    wx.uploadFile({
      url: `${apiBaseUrl()}${path}`,
      filePath,
      name,
      header: { Authorization: `Bearer ${token}` },
      success(response) {
        if (shouldRefreshCustomerSession(path, response.statusCode, retried)) {
          guestSessionManager.invalidate()
          refreshGuestToken()
            .then(() => upload<T>(path, filePath, name, true))
            .then(resolve, reject)
          return
        }
        handleUploadEnvelope<T>(response, resolve, reject)
      },
      fail(error) {
        reject(new ApiError(error.errMsg || '网络连接失败', 'NETWORK_ERROR'))
      },
    })
  }))
}

function needsCustomerToken(path: string): boolean {
  return !path.startsWith('/public/')
}

export function shouldRefreshCustomerSession(
  path: string,
  status: number,
  retried: boolean,
): boolean {
  return status === 401 && !retried && needsCustomerToken(path)
}

function customerHeader(path: string): Promise<Record<string, string>> {
  if (!needsCustomerToken(path)) return Promise.resolve({})
  return ensureGuestToken().then((token) => ({
    Authorization: `Bearer ${token}`,
  }))
}

function handleUploadEnvelope<T>(
  response: { data: string; statusCode: number },
  resolve: (value: T) => void,
  reject: (reason: unknown) => void,
): void {
  let envelope: UploadEnvelope<T> | null = null
  try {
    envelope = JSON.parse(response.data) as UploadEnvelope<T>
  } catch {
    reject(new ApiError('上传响应格式不合法', 'COMMON_INTERNAL_ERROR'))
    return
  }
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
      envelope.message || '上传失败',
      envelope.code || 'COMMON_INTERNAL_ERROR',
      envelope.traceId,
    ),
  )
}
