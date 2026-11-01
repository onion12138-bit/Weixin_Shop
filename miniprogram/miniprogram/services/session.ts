interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface GuestSession {
  customerIdentityId: number
  accessToken: string
  expiresInSeconds: number
}

export interface StringStorage {
  get(key: string): string | null
  set(key: string, value: string): void
  remove(key: string): void
}

export interface SessionTransport {
  exchange(installationId: string): Promise<GuestSession>
}

export interface GuestSessionManager {
  ensure(): Promise<string>
  refresh(failedToken?: string): Promise<string>
  invalidate(): void
  currentToken(): string | null
}

export const INSTALLATION_ID_KEY = 'diy_installation_id'
export const CUSTOMER_TOKEN_KEY = 'diy_customer_access_token'

export function createGuestSessionManager(
  storage: StringStorage,
  transport: SessionTransport,
  uuidFactory: () => string | Promise<string> = createUuidV4,
): GuestSessionManager {
  let inFlightRefresh: Promise<string> | null = null

  async function installationId(): Promise<string> {
    const existing = storage.get(INSTALLATION_ID_KEY)
    if (existing) return existing
    const generated = await uuidFactory()
    storage.set(INSTALLATION_ID_KEY, generated)
    return generated
  }

  async function refresh(failedToken?: string): Promise<string> {
    // 迟到的旧请求不能清掉其他请求刚刷新的令牌。
    const current = storage.get(CUSTOMER_TOKEN_KEY)
    if (failedToken && current && current !== failedToken) return current
    if (inFlightRefresh) return inFlightRefresh
    if (failedToken && current === failedToken) storage.remove(CUSTOMER_TOKEN_KEY)
    // 锁覆盖随机数生成阶段，首次并发请求也只能创建一个安装身份。
    inFlightRefresh = installationId()
      .then((id) => transport.exchange(id))
      .then((session) => {
        if (!session || typeof session.accessToken !== 'string' || !session.accessToken) {
          throw new Error('建立访客会话失败')
        }
        storage.set(CUSTOMER_TOKEN_KEY, session.accessToken)
        return session.accessToken
      })
      .finally(() => {
        inFlightRefresh = null
      })
    return inFlightRefresh
  }

  async function ensure(): Promise<string> {
    const token = storage.get(CUSTOMER_TOKEN_KEY)
    if (token) return token
    return refresh()
  }

  return {
    ensure,
    refresh,
    invalidate: () => storage.remove(CUSTOMER_TOKEN_KEY),
    currentToken: () => storage.get(CUSTOMER_TOKEN_KEY),
  }
}

export const guestSessionManager = createGuestSessionManager(
  {
    get: (key) => wx.getStorageSync(key) || null,
    set: (key, value) => wx.setStorageSync(key, value),
    remove: (key) => wx.removeStorageSync(key),
  },
  {
    exchange: exchangeGuestSession,
  },
)

export function ensureGuestToken(): Promise<string> {
  return guestSessionManager.ensure()
}

export function refreshGuestToken(failedToken?: string): Promise<string> {
  return guestSessionManager.refresh(failedToken)
}

function exchangeGuestSession(installationId: string): Promise<GuestSession> {
  return new Promise((resolve, reject) => {
    wx.request<ApiEnvelope<GuestSession>>({
      url: `${apiBaseUrl()}/app/session/guest`,
      method: 'POST',
      data: { installationId },
      timeout: 10000,
      success(response) {
        const envelope = response.data
        if (
          response.statusCode >= 200 &&
          response.statusCode < 300 &&
          envelope?.code === 'OK'
        ) {
          resolve(envelope.data)
          return
        }
        reject(new Error(envelope?.message || '建立访客会话失败'))
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'))
      },
    })
  })
}

function apiBaseUrl(): string {
  const app = getApp<{
    globalData: { apiBaseUrl: string }
  }>()
  return app.globalData.apiBaseUrl.replace(/\/$/, '')
}

export async function createUuidV4(): Promise<string> {
  const bytes = await randomBytes()
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) =>
    byte.toString(16).padStart(2, '0'),
  )
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-')
}

function randomBytes(): Promise<Uint8Array> {
  // 安装标识可用于恢复访客身份，不能降级为可预测的 Math.random。
  return new Promise((resolve, reject) => {
    const manager = typeof wx !== 'undefined' ? wx.getUserCryptoManager?.() : undefined
    if (!manager?.getRandomValues) {
      reject(new Error('当前微信版本不支持安全登录，请更新微信后重试'))
      return
    }
    manager.getRandomValues({
      length: 16,
      success(result) {
        if (!(result.randomValues instanceof ArrayBuffer) || result.randomValues.byteLength !== 16) {
          reject(new Error('安全随机数生成失败，请重试'))
          return
        }
        resolve(new Uint8Array(result.randomValues))
      },
      fail() { reject(new Error('安全随机数生成失败，请重试')) },
    })
  })
}
