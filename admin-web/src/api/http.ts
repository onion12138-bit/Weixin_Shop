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

const TOKEN_KEY = 'diy_bracelet_admin_access_token'

/**
 * 将后端返回的相对资源地址转换为可直接展示的绝对地址。
 *
 * 管理端开发服务器通常运行在 5173 端口，而图片接口运行在当前后端端口（本地默认 8081）。
 * 如果直接把 `/api/public/files/...` 交给 img 标签，浏览器会错误地向
 * 5173 端口请求，因此这里统一补齐后端源站。
 */
export function resolveApiResourceUrl(resourceUrl: string): string {
  if (!resourceUrl || /^https?:\/\//i.test(resourceUrl)) {
    return resourceUrl
  }
  const apiUrl = new URL(API_BASE_URL, window.location.origin)
  return new URL(resourceUrl, apiUrl.origin).toString()
}

/**
 * 读取本地管理员令牌。令牌只放入 Authorization 请求头，不写入日志。
 */
export function getAccessToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  window.localStorage.removeItem(TOKEN_KEY)
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
  })
  const envelope = (await response.json()) as ApiEnvelope<T>

  if (!response.ok || envelope.code !== 'OK') {
    if (response.status === 401) {
      clearAccessToken()
    }
    throw new ApiError(
      envelope.message || '请求失败',
      envelope.code || 'COMMON_INTERNAL_ERROR',
      response.status,
      envelope.traceId,
    )
  }

  return envelope.data
}
