import { clearAccessToken, getAccessToken } from '../utils/access-token'

export {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from '../utils/access-token'

/**
 * 后端统一响应结构。
 */
interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

/**
 * 保留后端业务码和 traceId 的前端请求异常，便于页面展示和问题定位。
 */
export class ApiError extends Error {
  readonly code: string
  readonly traceId?: string
  readonly status: number

  constructor(
    message: string,
    code: string,
    status: number,
    traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api'
).replace(/\/$/, '')

/**
 * 将后端返回的相对资源地址转换为可直接展示的绝对地址。
 *
 * 管理端开发服务器通常运行在 5173 端口，而图片接口运行在当前后端端口（本地默认 8081）。
 * 如果直接把 `/api/public/files/...` 交给 img 标签，浏览器会错误地向
 * 5173 端口请求，因此这里统一补齐后端源站。
 */
export function resolveApiResourceUrl(resourceUrl: string): string {
  if (!resourceUrl) return ''
  try {
    const apiUrl = new URL(API_BASE_URL, window.location.origin)
    const url = new URL(resourceUrl, apiUrl.origin)
    // 资源可能用于新窗口打开，只接受网络图片，拒绝脚本和内嵌文档协议。
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return ''
    return url.toString()
  } catch {
    return ''
  }
}

/** 非 JSON 或结构异常的响应也转换为稳定的 ApiError，不泄露错误页正文。 */
async function readEnvelope<T>(response: Response): Promise<Partial<ApiEnvelope<T>>> {
  try {
    const body: unknown = await response.json()
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
      return {}
    }
    const envelope = body as Record<string, unknown>
    return {
      code: typeof envelope.code === 'string' ? envelope.code : undefined,
      message: typeof envelope.message === 'string' ? envelope.message : undefined,
      traceId: typeof envelope.traceId === 'string' ? envelope.traceId : undefined,
      data: envelope.data as T,
    }
  } catch {
    return {}
  }
}

/**
 * 调用后端并解包统一响应。401 会清理失效令牌，让根组件回到登录页。
 */
export async function request<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    // 订单、客户地址、API Key 等管理数据不写入浏览器 HTTP 缓存。
    cache: 'no-store',
  })
  if (response.status === 401) {
    clearAccessToken(token)
  }
  const envelope = await readEnvelope<T>(response)

  if (!response.ok || envelope.code !== 'OK') {
    throw new ApiError(
      envelope.message || '请求失败',
      envelope.code || 'COMMON_INTERNAL_ERROR',
      response.status,
      envelope.traceId,
    )
  }

  return envelope.data as T
}

/**
 * 下载需要管理员鉴权的二进制文件；错误响应仍按统一 JSON 契约解析。
 */
export async function requestBlob(
  path: string,
  init: RequestInit = {},
): Promise<Blob> {
  const headers = new Headers(init.headers)
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    cache: 'no-store',
  })
  if (response.ok) {
    return response.blob()
  }

  if (response.status === 401) {
    clearAccessToken(token)
  }
  const envelope = await readEnvelope<unknown>(response)
  throw new ApiError(
    envelope.message || '文件下载失败',
    envelope.code || 'COMMON_INTERNAL_ERROR',
    response.status,
    envelope.traceId,
  )
}
